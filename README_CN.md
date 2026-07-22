# InspireFace Android 示例

[English](README.md)

基于 InspireFace Android SDK (1.2.0) 与 CameraX 的示例。启动页采用方块网格功能菜单,
并提供全局模型选择(`Pikachu` / `Megatron`)。所选模型会在进入功能页时加载,每个功能页面
顶部也会用小字标注当前模型。

## 下载体验

<p>
  <a href="http://fir.tunm.top/pro/pz7b3dgv">
    <img src="docs/images/inspireface-android-example-app-download.png" width="220" alt="下载 InspireFace Android 示例">
  </a>
</p>

<p>
  <strong>直接下载 Android 示例进行体验</strong><br>
  扫描二维码，或<strong><a href="http://fir.tunm.top/pro/pz7b3dgv">下载 APP</a></strong>。
</p>

当前菜单包含八个独立页面:

- **静默活体(RGB Anti-Spoofing)**:对当前人脸逐帧输出活体分数,滑动窗口平均后按阈值判定真人/攻击。
- **动作活体(配合式)**:随机生成动作序列(眨眼 / 摇头 / 张嘴 / 抬头),逐个提示并检测完成情况,支持超时与人脸丢失失败重试。
- **姿态识别**:实时显示你做出的动作——最新动作大字显示,下方一排渐隐小字为历史动作(最多展示 6 条,上升沿去抖,同一姿势保持不会重复记录)。
- **人脸 1:1**:从本地选择两张图片,检测全部人脸并编号,默认选择第 1 张;点击 A/B 图中任意人脸框会立即切换并重新比对。圆形仪表展示转换后的相似度百分比,并按 SDK 推荐阈值给出判断。
- **人脸管理**:在当前模型的人脸库中搜索、新增、重命名、替换和删除身份。既支持相册多人脸框选，也支持相机自动录入：跟踪第 1 张人脸，稳定 1 秒后显示进度圈，再稳定 2 秒由红、黄变绿并自动采集；中途抖动会立即清空进度圈。
- **人脸识别**:在照片输入 / 视频流两个 Tab 间切换。照片模式检测并编号图片中的人脸，单人脸自动检索，多人脸点击框即可切换检索；照片下方的 Session 设置默认收起，可调检测输入 px、最大人脸数和最小人脸 px，应用后持久化到本机。视频模式复用 CameraX 跟踪链，只跟踪第一张人脸，稳定约 1 秒后搜索当前模型库并在预览底部显示结果，支持前后镜头切换。
- **人脸跟踪**:检测并编号图片中的全部人脸，点击人脸框切换选中的 SDK 原生 106 点稠密关键点。中等和大人脸直接在原图叠加，真正偏小的人脸使用右下角 2.4 倍检测框裁剪放大器；视频跟踪 Tab 使用 OpenGL 绘制同 Track ID 配色的四角框和 106 点。
- **人脸属性分析**:对图片中选中的人脸展示口罩、年龄段、画面质量、表情状态、民族、性别及左右眼状态。点击其他编号人脸会立即更新结果，小人脸复用右下角扩大裁剪放大镜。

各相机页面的调试辅助:

- **欧拉角**:实时显示当前跟踪人脸的 Yaw / Pitch / Roll(约 10Hz 刷新;注意 1.2.0 JNI 仅 `angles[0]` 可信,多人脸时显示的是首个人脸)。
- **关键点**当前隐藏;`LandmarkGlView` 仍作为 OpenGL ES 2.0 叠加层保留,后续可接入新的菜单入口或调试开关。

**切换镜头**按钮支持前后摄像头运行时切换。SDK 侧无需任何改动:每帧都按自身 `rotationDegrees`
预旋转后以 `CAMERA_ROTATION_0` 转正 buffer 送入 InspireFace,新镜头的传感器方向逐帧被吸收——
只需翻转显示镜像并复位模式状态机。

语言:首次启动默认**英文**(不跟随系统语言),主页和相机页面的语言按钮均可在中英文之间
切换。Android 13 及以上也会在系统的“应用语言”设置中显示中英文选项,选择会持久化。

## 架构

```
CameraX ImageAnalysis (YUV_420_888, 640x480, KEEP_ONLY_LATEST)
   └─ UprightFaceCameraAnalyzer(分析线程,单线程执行器)
        ├─ Nv21Converter.convert()        YUV_420_888 → 紧凑 NV21(首帧探测 VU 交织,之后单次 bulk copy)
        ├─ Nv21Converter.rotateUpright()  Java 侧预旋转为竖直方向
        ├─ CreateImageStreamFromByteBuffer(nv21, CAMERA_ROTATION_0)
        ├─ ExecuteFaceTrack               LIGHT_TRACK 模式
        ├─ FaceAnalyzer / EnrollmentFaceAnalyzer
        │    └─ 活体模式 pipeline 或首张人脸稳定状态机
        └─ ReleaseImageStream             同帧内释放
```

- `HomeActivity` — 方块网格功能菜单与全局模型选择
- `FaceCompareActivity` — 本地图片解码、人脸特征提取与 1:1 比对
- `FaceManagementActivity` — 模型隔离的人脸身份、裁剪图与 FeatureHub 增删改查界面
- `FaceRecognitionActivity` / `StillImageSessionSettings` — 照片多人脸选择、模型库 1:N 检索与持久化 Session 参数
- `view/RecognitionFaceAnalyzer` — 视频流首人脸稳定门、特征提取与模型库检索
- `FaceDetectionActivity` / `widget/FaceLandmarkOverlayView` — 图像多人脸检测、106 点叠加与小人脸放大器
- `FaceAttributeActivity` / `face/FaceAttributeProcessor` — 图片可选人脸的口罩、质量、人口与交互属性
- `view/FaceCaptureActivity` / `EnrollmentFaceAnalyzer` — 首张人脸稳定判断与自动相机录入
- `view/CameraPreviewController` — 可复用的 CameraX 预览、4:3 分析、镜头兜底与前后切换
- `view/UprightFaceCameraAnalyzer` — 共用的 YUV→转正 NV21、人脸跟踪及 native Stream/Session 生命周期
- `face/FaceImageProcessor` / `face/FaceCropUtils` / `widget/FaceImageOverlayView` — 共用的多人脸提取、扩大裁剪和可点击编号框
- `face/FaceRepository` — 按模型隔离的持久化 FeatureHub、裁剪图和元数据
- `view/LivenessActivity` — 共用 CameraX 页面,同时作为静默活体入口
- `view/ActionLivenessActivity` / `PoseActivity` — 固定控制器模式的独立页面路由
- `view/FaceAnalyzer` — 活体模式 pipeline、性能统计与调试读数
- `view/LivenessController` — 两种模式的判定状态机(阈值等可调参数集中在此)
- `view/Nv21Converter` — YUV→NV21 快速转换 + NV21 旋转
- `view/FaceOverlayView` — 人脸框叠加(center-crop 映射 + 前置镜像)
- `view/FaceEngine` — 支持模型切换的 GlobalLaunch/GlobalTerminate 与 Session 创建
- `FaceModelPrefs` / `LocalePrefs` / `App` — 全局模型和应用语言持久化

## 人脸数据按模型隔离

Pikachu 和 Megatron 不共用人脸特征、裁剪图、元数据或 ID 序列。数据分别保存在应用私有路径:

```text
files/face_hub/Pikachu/features.db
files/face_hub/Pikachu/crops/
shared_prefs/face_records_Pikachu.xml

files/face_hub/Megatron/features.db
files/face_hub/Megatron/crops/
shared_prefs/face_records_Megatron.xml
```

FeatureHub 使用手动主键和持久化数据库。切换全局模型时,会同时切换 native 特征库、
裁剪图目录和元数据集合。

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

- JDK 17(AGP 8.6.1 要求;Android Studio 自带的 JDK 即可)
- Android Studio Ladybug 及以上——Gradle 8.7 wrapper 已提交,无需本地安装 Gradle
- 首次同步需要能访问 `google()`、`mavenCentral()` 和 `jitpack.io`
  (InspireFace SDK 及内置模型包从 JitPack 拉取)
- Android 7.0 / API 24 及以上的 ARM 设备。项目以 Android 15 / API 35 编译并作为
  target,Android 本身不设置安装版本上限。
- SDK 仅含 arm64-v8a / armeabi-v7a,因此 x86/x86_64 模拟器与仅 Intel 的 ChromeOS
  设备不能运行 native 人脸引擎；arm64 SDK 库与本项目兼容桥均支持 Android 15 的
  16 KB 内存页设备。

`local.properties` 不入库;Android Studio 会自动生成,命令行构建可设置 `ANDROID_HOME`。

## 运行

首次进入功能页会从 assets 解压模型包,耗时略长。

```bash
./gradlew :app:installDebug
```

本项目已覆盖活体、姿态、1:1、人脸管理与 FeatureHub 1:N 照片检索；需要其他 SDK
能力时，可继续参考上游 [InspireFace](https://github.com/HyperInspire/InspireFace)
的 Android example。
