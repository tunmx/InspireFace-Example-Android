package com.example.inspireface_example.liveness;

import android.content.Context;
import android.util.Log;

import com.insightface.sdk.inspireface.InspireFace;
import com.insightface.sdk.inspireface.base.CustomParameter;
import com.insightface.sdk.inspireface.base.Session;

/**
 * Process-wide InspireFace lifecycle: one GlobalLaunch, session factory tuned for
 * low-latency front-camera preview.
 */
final class FaceEngine {

    private static final String TAG = "FaceEngine";

    /** Model pack to load. PIKACHU is the lightweight mobile pack. */
    private static final String MODEL_PACK = InspireFace.PIKACHU;

    /** Max faces tracked per frame — enough to notice "more than one face" and stay cheap. */
    private static final int MAX_FACES = 3;

    /**
     * Long-edge size the tracker scales frames to before detection. Matches the default
     * 320 detect level; in LIGHT_TRACK mode detection is amortized (~1 in 20 frames) so
     * this costs little.
     */
    private static final int TRACK_PREVIEW_SIZE = 320;

    private static volatile boolean launched;

    private FaceEngine() {
    }

    /** Idempotent; safe to call from any thread. Slow on first run (copies model assets). */
    static synchronized boolean ensureLaunched(Context context) {
        if (!launched) {
            launched = Boolean.TRUE.equals(
                    InspireFace.GlobalLaunch(context.getApplicationContext(), MODEL_PACK));
            Log.i(TAG, "GlobalLaunch(" + MODEL_PACK + ") -> " + launched);
        }
        return launched;
    }

    /**
     * Session for continuous video tracking with both liveness models loaded. All calls on
     * the returned session must stay on a single thread.
     */
    static Session createPreviewSession() {
        // Face quality also loads the pose model — without it yaw/pitch stay 0 and the
        // shake/head-raise actions can never fire.
        CustomParameter parameter = InspireFace.CreateCustomParameter()
                .enableLiveness(true)
                .enableInteractionLiveness(true)
                .enableFaceQuality(true);
        Session session = InspireFace.CreateSession(
                parameter, InspireFace.DETECT_MODE_LIGHT_TRACK, MAX_FACES, -1, -1);
        if (session == null) {
            return null;
        }
        InspireFace.SetTrackPreviewSize(session, TRACK_PREVIEW_SIZE);
        InspireFace.SetFaceDetectThreshold(session, 0.5f);
        InspireFace.SetFilterMinimumFacePixelSize(session, 0);
        return session;
    }
}
