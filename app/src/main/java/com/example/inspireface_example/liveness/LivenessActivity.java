package com.example.inspireface_example.liveness;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.inspireface_example.LocalePrefs;
import com.example.inspireface_example.R;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.insightface.sdk.inspireface.base.FaceEulerAngle;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Front-camera liveness test screen with two switchable modes: silent RGB anti-spoofing
 * and cooperative action challenges. Frames reach InspireFace as NV21 through
 * CreateImageStreamFromByteBuffer for the lowest-latency path.
 */
public class LivenessActivity extends AppCompatActivity implements FaceAnalyzer.Listener {

    private PreviewView previewView;
    private FaceOverlayView overlayView;
    private LandmarkGlView landmarkView;
    private SwitchMaterial switchLandmarks;
    private TextView promptTitle;
    private TextView promptSub;
    private TextView perfText;
    private TextView eulerText;
    private SwitchMaterial switchEuler;
    private LinearProgressIndicator promptProgress;
    private View btnRestart;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private LivenessController controller;
    private FaceAnalyzer analyzer;
    private LivenessController.UiState lastState;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startEngine();
                } else {
                    promptTitle.setText(R.string.msg_permission_required);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_liveness);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.faceOverlay);
        landmarkView = findViewById(R.id.landmarkGlView);
        promptTitle = findViewById(R.id.promptTitle);
        promptSub = findViewById(R.id.promptSub);
        perfText = findViewById(R.id.perfText);
        eulerText = findViewById(R.id.eulerText);
        switchEuler = findViewById(R.id.switchEuler);
        switchLandmarks = findViewById(R.id.switchLandmarks);
        promptProgress = findViewById(R.id.promptProgress);
        btnRestart = findViewById(R.id.btnRestart);

        switchEuler.setOnCheckedChangeListener((button, checked) -> {
            if (analyzer != null) {
                analyzer.setEulerEnabled(checked);
            }
            eulerText.setText(R.string.euler_no_face);
            eulerText.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        // The landmark switch is commented out of the layout for now; keep the wiring
        // null-guarded so restoring the XML is all it takes to re-enable it.
        if (switchLandmarks != null) {
            switchLandmarks.setOnCheckedChangeListener((button, checked) -> {
                if (analyzer != null) {
                    analyzer.setLandmarksEnabled(checked);
                }
                if (!checked) {
                    landmarkView.clearPoints();
                }
                landmarkView.setVisibility(checked ? View.VISIBLE : View.GONE);
            });
        }

        applyWindowInsets();

        controller = new LivenessController(this);

        MaterialButtonToggleGroup modeToggle = findViewById(R.id.modeToggle);
        modeToggle.check(R.id.btnModeSilent);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnModeSilent) {
                    controller.setMode(LivenessController.Mode.SILENT);
                } else if (checkedId == R.id.btnModeAction) {
                    controller.setMode(LivenessController.Mode.ACTION);
                } else {
                    controller.setMode(LivenessController.Mode.POSE);
                }
            }
        });
        btnRestart.setOnClickListener(v -> controller.restart());
        // In-app language toggle (bottom-right): English by default, Chinese on demand.
        findViewById(R.id.langSwitch).setOnClickListener(v -> LocalePrefs.toggle(this));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startEngine();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void applyWindowInsets() {
        View topBar = findViewById(R.id.topBar);
        MaterialCardView promptCard = findViewById(R.id.promptCard);
        int cardBaseMargin = (int) (24 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.livenessRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topBar.getPaddingLeft(), bars.top,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) promptCard.getLayoutParams();
            lp.bottomMargin = cardBaseMargin + bars.bottom;
            promptCard.setLayoutParams(lp);
            return insets;
        });
    }

    /** GlobalLaunch copies model assets on first run — keep it off the main thread. */
    private void startEngine() {
        promptTitle.setText(R.string.msg_initializing);
        analysisExecutor.execute(() -> {
            boolean ok = FaceEngine.ensureLaunched(this);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) {
                    return; // model copy can outlive the activity — don't bind a dead lifecycle
                }
                if (ok) {
                    bindCamera();
                } else {
                    promptTitle.setText(R.string.msg_engine_failed);
                }
            });
        });
    }

    private void bindCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isDestroyed() || isFinishing()) {
                return;
            }
            ProcessCameraProvider provider;
            try {
                provider = future.get();
            } catch (ExecutionException | InterruptedException e) {
                promptTitle.setText(R.string.msg_engine_failed);
                return;
            }
            try {
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    promptTitle.setText(R.string.msg_no_front_camera);
                    return;
                }
            } catch (CameraInfoUnavailableException e) {
                promptTitle.setText(R.string.msg_no_front_camera);
                return;
            }

            // 640x480 keeps NV21 conversion + tracking cheap; 4:3 on both use cases keeps
            // the overlay mapping consistent with what PreviewView shows.
            ResolutionSelector analysisResolution = new ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build();
            ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analyzer = new FaceAnalyzer(controller, overlayView, landmarkView, this, true);
            analyzer.setEulerEnabled(switchEuler.isChecked());
            analyzer.setLandmarksEnabled(switchLandmarks != null && switchLandmarks.isChecked());
            analysis.setAnalyzer(analysisExecutor, analyzer);

            Preview preview = new Preview.Builder()
                    .setResolutionSelector(new ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build())
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
        }, ContextCompat.getMainExecutor(this));
    }

    // ------------------------------------------------------------------
    // FaceAnalyzer.Listener (analysis thread)
    // ------------------------------------------------------------------

    @Override
    public void onUiState(LivenessController.UiState state) {
        runOnUiThread(() -> {
            if (state.sameContent(lastState)) {
                return;
            }
            lastState = state;
            promptTitle.setText(state.title);
            if (state.subtitle != null) {
                promptSub.setText(state.subtitle);
                promptSub.setVisibility(View.VISIBLE);
            } else {
                promptSub.setVisibility(View.GONE);
            }
            if (state.progress >= 0) {
                promptProgress.setProgress(state.progress);
                promptProgress.setVisibility(View.VISIBLE);
            } else {
                promptProgress.setVisibility(View.GONE);
            }
            btnRestart.setVisibility(state.showRestart ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onPerf(double fps, long latencyMs) {
        runOnUiThread(() -> {
            perfText.setVisibility(View.VISIBLE);
            perfText.setText(String.format(Locale.US,
                    getString(R.string.perf_format), fps, latencyMs));
        });
    }

    @Override
    public void onEulerAngles(FaceEulerAngle angle) {
        runOnUiThread(() -> {
            if (!switchEuler.isChecked()) {
                return;
            }
            if (angle == null) {
                eulerText.setText(R.string.euler_no_face);
            } else {
                eulerText.setText(String.format(Locale.US,
                        getString(R.string.euler_format), angle.yaw, angle.pitch, angle.roll));
            }
        });
    }

    @Override
    public void onSessionError() {
        runOnUiThread(() -> promptTitle.setText(R.string.msg_engine_failed));
    }

    // GL lifecycle follows visibility (start/stop), not focus (resume/pause): in
    // multi-window the activity can be paused but visible with the camera and analysis
    // still running — the landmark overlay must keep rendering there.
    @Override
    protected void onStart() {
        super.onStart();
        landmarkView.onResume();
    }

    @Override
    protected void onStop() {
        landmarkView.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (analyzer != null) {
            FaceAnalyzer toRelease = analyzer;
            analysisExecutor.execute(toRelease::release);
        }
        analysisExecutor.shutdown();
    }
}
