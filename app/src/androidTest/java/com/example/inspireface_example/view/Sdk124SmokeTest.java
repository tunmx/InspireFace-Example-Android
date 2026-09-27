package com.example.inspireface_example.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.inspireface_example.face.FaceAttributeProcessor;
import com.example.inspireface_example.plus.EyeCrop320;
import com.example.inspireface_example.plus.RgbPixels;
import com.insightface.sdk.inspireface.FaceCapture;
import com.insightface.sdk.inspireface.FaceDetectionSnapshot;
import com.insightface.sdk.inspireface.InspireFace;
import com.insightface.sdk.inspireface.base.CustomParameter;
import com.insightface.sdk.inspireface.base.FaceCaptureConfig;
import com.insightface.sdk.inspireface.base.FaceCaptureProgress;
import com.insightface.sdk.inspireface.base.FaceFeature;
import com.insightface.sdk.inspireface.base.ImageStream;
import com.insightface.sdk.inspireface.base.InspireFaceVersion;
import com.insightface.sdk.inspireface.base.MultipleFaceData;
import com.insightface.sdk.inspireface.base.Point2f;
import com.insightface.sdk.inspireface.base.Session;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

/** Exercises the packaged Java/native boundary and model assets on an Android device. */
@RunWith(AndroidJUnit4.class)
public final class Sdk124SmokeTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void packagedNativeVersionIs124() {
        InspireFaceVersion version = InspireFace.QueryInspireFaceVersion();
        assertNotNull(version);
        assertEquals(1, version.major);
        assertEquals(2, version.minor);
        assertEquals(4, version.patch);
    }

    @Test
    public void bundledModelsSupportAppSessionsAndPipelines() throws Exception {
        Bitmap bitmap = loadSampleFace();
        try {
            for (String model : new String[]{"Pikachu", "Megatron"}) {
                assertTrue("Launch " + model, InspireFace.GlobalLaunch(context, model));
                try {
                    checkSession(FaceEngine.createDetectionSession(320, 3, 0), bitmap, false, false);
                    checkSession(FaceEngine.createFaceTrackingSession(
                            InspireFace.DETECT_MODE_LIGHT_TRACK, 320, 3, 0), bitmap, false, false);
                    checkSession(FaceEngine.createFaceTrackingSession(
                            InspireFace.DETECT_MODE_TRACK_BY_DETECTION, 320, 3, 0), bitmap, false, false);
                    checkSession(FaceEngine.createRecognitionSession(), bitmap, true, false);
                    checkSession(FaceEngine.createVideoRecognitionSession(), bitmap, true, false);
                    checkSession(FaceEngine.createPreviewSession(), bitmap, false, true);
                    checkPlusPreprocessing(bitmap);

                    Session attributes = FaceEngine.createAttributeSession();
                    assertNotNull("Attribute session", attributes);
                    try {
                        FaceAttributeProcessor.Result result = FaceAttributeProcessor.analyze(attributes, bitmap);
                        assertEquals(FaceAttributeProcessor.Status.READY, result.status);
                        assertTrue(result.attributes.length > 0);
                    } finally {
                        FaceEngine.releaseSession(attributes);
                    }
                } finally {
                    assertTrue(InspireFace.GlobalTerminate());
                }
            }
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void captureAndSnapshotExtensionsMatchNativeLibrary() throws Exception {
        assertTrue(InspireFace.GlobalLaunch(context, "Pikachu"));
        try {
            Bitmap bitmap = loadSampleFace();
            try {
                Session session = FaceEngine.createPreviewSession();
                assertNotNull(session);
                try {
                    ImageStream stream = InspireFace.CreateImageStreamFromBitmap(bitmap, InspireFace.CAMERA_ROTATION_0);
                    assertNotNull(stream);
                    try {
                        FaceCaptureConfig config = FaceCapture.defaultConfig();
                        config.filterMask |= FaceCapture.FILTER_POSE | FaceCapture.FILTER_QUALITY;
                        try (FaceCapture capture = FaceCapture.create(session, config);
                             FaceDetectionSnapshot snapshot = FaceDetectionSnapshot.create(session, stream)) {
                            FaceCaptureProgress progress = capture.update(stream, snapshot, 1L, 1L);
                            assertEquals(1L, progress.frameId);
                            assertEquals(1L, progress.timestampMs);
                            assertNotNull(capture.getResults());
                            assertNotNull(capture.finish());
                            capture.reset();
                        }
                    } finally {
                        InspireFace.ReleaseImageStream(stream);
                    }
                } finally {
                    FaceEngine.releaseSession(session);
                }
            } finally {
                bitmap.recycle();
            }
        } finally {
            assertTrue(InspireFace.GlobalTerminate());
        }
    }

    private Bitmap loadSampleFace() throws Exception {
        try (InputStream input = context.getAssets().open("inspireface/kun.jpg")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            assertNotNull("Bundled SDK sample image", bitmap);
            return bitmap;
        }
    }

    /** Uses only the bundled image locally; this test never contacts the PLUS service. */
    private void checkPlusPreprocessing(Bitmap bitmap) {
        Session session = FaceEngine.createPlusSession();
        assertNotNull(session);
        try {
            ImageStream stream = InspireFace.CreateImageStreamFromBitmap(bitmap, InspireFace.CAMERA_ROTATION_0);
            assertNotNull(stream);
            try {
                MultipleFaceData faces = InspireFace.ExecuteFaceTrack(session, stream);
                assertNotNull(faces);
                assertTrue(faces.detectedNum > 0);
                Point2f[] points = InspireFace.GetFaceDenseLandmarkFromFaceToken(faces.tokens[0]);
                assertEquals(106, points.length);
                Point2f a = points[67], b = points[68];
                if (a.x > b.x) { Point2f swap = a; a = b; b = swap; }
                assertTrue(a.x >= 0 && b.x < bitmap.getWidth() && b.x - a.x >= 2);
                assertTrue(a.y >= 0 && a.y < bitmap.getHeight() && b.y >= 0 && b.y < bitmap.getHeight());
                assertTrue(InspireFace.MultipleFacePipelineProcess(session, stream, faces,
                        InspireFace.CreateCustomParameter().enableFaceQuality(true)));
                assertTrue(InspireFace.GetFaceQualityConfidence(session).num > 0);
                int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                assertEquals(307200, RgbPixels.fromArgb(EyeCrop320.crop(pixels,
                        bitmap.getWidth(), bitmap.getHeight(), a.x, a.y, b.x, b.y)).length);
                MultipleFaceData next = InspireFace.ExecuteFaceTrack(session, stream);
                assertEquals("A steady face must keep its identity", faces.trackIds[0], next.trackIds[0]);
            } finally { InspireFace.ReleaseImageStream(stream); }
            Bitmap blank = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                ImageStream empty = InspireFace.CreateImageStreamFromBitmap(blank, InspireFace.CAMERA_ROTATION_0);
                assertNotNull(empty);
                try {
                    MultipleFaceData lost = InspireFace.ExecuteFaceTrack(session, empty);
                    assertNotNull(lost);
                    assertEquals("A missing face must be reported on the next frame", 0, lost.detectedNum);
                } finally { InspireFace.ReleaseImageStream(empty); }
            } finally { blank.recycle(); }
        } finally { FaceEngine.releaseSession(session); }
    }

    private void checkSession(Session session, Bitmap bitmap, boolean recognition, boolean liveness) {
        assertNotNull("CreateSession JNI and model compatibility", session);
        try {
            ImageStream stream = InspireFace.CreateImageStreamFromBitmap(bitmap, InspireFace.CAMERA_ROTATION_0);
            assertNotNull(stream);
            try {
                MultipleFaceData faces = InspireFace.ExecuteFaceTrack(session, stream);
                assertNotNull(faces);
                assertTrue("Detect the bundled sample face", faces.detectedNum > 0);
                assertNotNull("Owned token payload from JNI", faces.tokens[0].data);
                assertTrue(faces.tokens[0].size > 0);
                assertEquals(faces.tokens[0].size, faces.tokens[0].data.length);
                Point2f[] landmarks = InspireFace.GetFaceDenseLandmarkFromFaceToken(faces.tokens[0]);
                assertNotNull(landmarks);
                assertEquals(106, landmarks.length);
                if (recognition) {
                    FaceFeature feature = InspireFace.ExtractFaceFeature(session, stream, faces.tokens[0]);
                    assertNotNull(feature);
                    assertEquals(InspireFace.GetFeatureLength(), feature.data.length);
                    assertTrue(InspireFace.FaceComparison(feature, feature) > 0.99f);
                }
                if (liveness) {
                    CustomParameter pipeline = InspireFace.CreateCustomParameter()
                            .enableLiveness(true).enableFaceQuality(true).enableInteractionLiveness(true);
                    assertTrue("Pipeline JNI parameter layout", InspireFace.MultipleFacePipelineProcess(
                            session, stream, faces, pipeline));
                    assertNotNull(InspireFace.GetRGBLivenessConfidence(session));
                    assertNotNull(InspireFace.GetFaceInteractionActionsResult(session));
                }
            } finally {
                InspireFace.ReleaseImageStream(stream);
            }
        } finally {
            FaceEngine.releaseSession(session);
        }
    }
}
