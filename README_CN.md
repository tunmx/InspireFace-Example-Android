# InspireFace Example — 前置相机活体检测

[English](README.md)

基于 InspireFace Android SDK (1.2.0) 的前置相机实时活体检测示例,支持三种可切换模式:

- **静默活体(RGB Anti-Spoofing)**:对当前人脸逐帧输出活体分数,滑动窗口平均后按阈值判定真人/攻击。
- **动作活体(配合式)**:随机生成动作序列(眨眼 / 摇头 / 张嘴 / 抬头),逐个提示并检测完成情况,支持超时与人脸丢失失败重试。
- **姿态识别**:实时显示你做出的动作——最新动作大字显示,下方一排渐隐小字为历史动作(最多展示 6 条,上升沿去抖,同一姿势保持不会重复记录)。

调试辅助(顶部两个开关):

- **欧拉角**:实时显示当前跟踪人脸的 Yaw / Pitch / Roll(约 10Hz 刷新;注意 1.2.0 JNI 仅 `angles[0]` 可信,多人脸时显示的是首个人脸)。
- **关键点**(暂时隐藏——在 `activity_liveness.xml` 中已注释,Java 侧做了空值保护,取消注释即可恢复):OpenGL ES 2.0 渲染 106 点稠密关键点(`LandmarkGlView`)——透明 GLSurfaceView 叠加层,每帧单次 `GL_POINTS` 绘制调用,坐标映射在顶点着色器内完成,`RENDERMODE_WHEN_DIRTY` 按需渲染,关闭时 surface 销毁零开销。

语言:UI 默认**英文**(不跟随系统语言),右下角按钮可在中英文之间切换(通过 per-app locale 持久化)。

## 架构

```
CameraX ImageAnalysis (YUV_420_888, 640x480, KEEP_ONLY_LATEST)
   └─ FaceAnalyzer(分析线程,单线程执行器)
        ├─ Nv21Converter.convert()        YUV_420_888 → 紧凑 NV21(首帧探测 VU 交织,之后单次 bulk copy)
        ├─ Nv21Converter.rotateUpright()  Java 侧预旋转为竖直方向
        ├─ CreateImageStreamFromByteBuffer(nv21, CAMERA_ROTATION_0)
        ├─ ExecuteFaceTrack               LIGHT_TRACK 模式
        ├─ LivenessController.onFrame()   按当前模式跑 MultipleFacePipelineProcess + 状态机
        └─ ReleaseImageStream             同帧内释放
```

- `liveness/LivenessActivity` — 相机绑定、权限、UI(模式切换 / 提示卡片 / FPS 指示)
- `liveness/FaceAnalyzer` — 每帧 NV21 转换、跟踪、性能统计
- `liveness/LivenessController` — 两种模式的判定状态机(阈值等可调参数集中在此)
- `liveness/Nv21Converter` — YUV→NV21 快速转换 + NV21 旋转
- `liveness/FaceOverlayView` — 人脸框叠加(center-crop 映射 + 前置镜像)
- `liveness/FaceEngine` — GlobalLaunch 与 Session 创建(Pikachu 轻量模型包)

## 关键设计决策(源码考证结论)

1. **Java 侧预旋转 NV21,恒传 `CAMERA_ROTATION_0`。**
   SDK(≤1.2.3)的 RGB 活体裁剪使用"旋转转正后的全图 + 未转正坐标系的人脸框",
   传 90/270 旋转常量时裁剪区域错位,静默活体分数失真。预旋转(640×480 约 1–2ms)
   彻底绕开该问题,且所有输出坐标直接就是显示方向,叠加层只需做前置镜像。
   另注意:SDK 的旋转常量与 Android `rotationDegrees` 方向相反
   (Android 90 → SDK ROTATION_270),预旋转方案也避免了这个坑。

2. **动作活体必须满足三个条件**(缺一动作永远不会触发):
   - `DETECT_MODE_LIGHT_TRACK`(其他模式每帧重建跟踪对象,动作时序窗口无法积累);
   - Session 创建时启用 `enableInteractionLiveness`;
   - Session 创建时启用 `enableFaceQuality`(加载姿态模型,否则 yaw/pitch 恒 0,摇头/抬头失效)。

3. **动作标志语义**(SDK 内部为 10 帧滑动窗口 + 规则判定):
   眨眼是单次脉冲(触发后窗口复位);摇头在窗口内锁存(约 10 次调用);张嘴/抬头是电平触发。
   因此控制器采用**边沿触发门**:每步动作先观察到标志为 0 才接受 1,防止上一动作残留误判。
   每个跟踪目标前 9 次 pipeline 调用为预热期(`normal=1`),等待阶段会预热窗口。

4. **`CreateImageStreamFromByteBuffer` 不拷贝数据**,native 侧持有 byte[] 指针。
   安全用法:同一帧内 create → track → pipeline → release,释放前不复写 byte[](本实现复用两块常驻缓冲,单线程串行,天然满足)。

5. **静默活体**:单帧分数,SDK 作者内置判定边界 0.88;每次 pipeline 调用都会做全帧格式转换(SDK 已知热点),
   故降频为每 2 帧一次 + 8 帧滑动平均,精度不损、开销减半。

6. **`MultipleFaceData.angles[i]` 仅 i==0 可信**(1.2.0 JNI 将首个人脸角度写入所有槽位),本实现只在单人脸时使用。

## 可调参数

集中在 `LivenessController` 顶部:

| 参数 | 默认值 | 说明 |
|---|---|---|
| `RGB_LIVENESS_THRESHOLD` | 0.88 | 静默活体判定阈值 |
| `SCORE_WINDOW` | 8 | 分数滑动平均窗口 |
| `SILENT_PIPELINE_INTERVAL` | 2 | 静默活体每 N 帧跑一次 pipeline |
| `ACTIONS_PER_RUN` | 3 | 每轮随机动作数 |
| `ACTION_TIMEOUT_MS` | 8000 | 单个动作超时 |
| `MIN_FACE_WIDTH_RATIO` | 0.18 | 最小人脸占画面宽度比例 |
| `POSE_HISTORY_MAX` | 6 | 姿态模式展示条数(1 条大字 + 5 条历史) |

## 环境要求

- JDK 17(AGP 8.5.1 要求;Android Studio 自带的 JDK 即可)
- Android Studio Koala 及以上——Gradle 8.7 wrapper 已提交,无需本地安装 Gradle
- 首次同步需要能访问 `google()`、`mavenCentral()` 和 `jitpack.io`
  (InspireFace SDK 及内置模型包从 JitPack 拉取)
- ARM 真机,minSdk 24(SDK 仅含 arm64-v8a / armeabi-v7a,x86 模拟器无法运行)

`local.properties` 不入库;Android Studio 会自动生成,命令行构建可设置 `ANDROID_HOME`。

## 运行

首次启动会从 assets 解压模型包,耗时略长。

```bash
./gradlew :app:installDebug
```

需要更完整的 SDK 能力示例(识别、FeatureHub 检索)请参考上游
[InspireFace](https://github.com/HyperInspire/InspireFace) 的 Android example——本仓库已移除
原模板的冒烟测试 Activity(可从 git 历史找回)。
