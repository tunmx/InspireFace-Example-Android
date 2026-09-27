package com.insightface.sdk.inspireface.base;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Checks the field descriptors required by the supplied 1.2.4 native binary. */
public final class JniDataContractTest {
    @Test
    public void faceTokensMatchNativeByteArrayContract() throws Exception {
        // GetFieldID(tokenClass, "data", "[B") is used by tracking, landmarks,
        // feature extraction, alignment and the per-face pipelines.
        assertEquals(byte[].class, FaceBasicToken.class.getField("data").getType());
        assertEquals(int.class, FaceBasicToken.class.getField("size").getType());
        assertEquals(FaceBasicToken[].class, MultipleFaceData.class.getField("tokens").getType());
        FaceBasicToken.class.getConstructor();
    }

    @Test
    public void sessionAndPipelineOptionsMatchNativeIntegerContract() throws Exception {
        for (String field : new String[]{
                "enableRecognition", "enableLiveness", "enableIrLiveness", "enableMaskDetect",
                "enableFaceQuality", "enableFaceAttribute", "enableInteractionLiveness",
                "enableFacePose", "enableFaceEmotion"}) {
            assertEquals(field, int.class, CustomParameter.class.getField(field).getType());
        }
    }
}
