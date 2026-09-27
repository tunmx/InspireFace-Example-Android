package com.insightface.sdk.inspireface.base;

/** Java parameter layout adapted for the InspireFace 1.2.4 JNI. */
public class CustomParameter {
    public int enableRecognition = 0;               // Enable face recognition
    public int enableLiveness = 0;                  // Enable liveness detection
    public int enableIrLiveness = 0;                // Enable IR liveness detection
    public int enableMaskDetect = 0;                // Enable mask detection
    public int enableFaceQuality = 0;               // Enable face quality detection
    public int enableFaceAttribute = 0;             // Enable face attribute detection
    public int enableInteractionLiveness = 0;        // Enable interaction liveness detection
    // Legacy field: 1.2.4 always enables landmarks in every detection mode.
    public int enableDetectModeLandmark = 0;

    public int enableFacePose = 0;                  // Enable face pose estimation
    public int enableFaceEmotion = 0;               // Enable face emotion recognition

    public CustomParameter() {}

    public CustomParameter enableFacePose(boolean enable) {
        this.enableFacePose = enable ? 1 : 0;
        return this;
    }

    public CustomParameter enableFaceEmotion(boolean enable) {
        this.enableFaceEmotion = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable face attribute detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableFaceAttribute(boolean enable) {
        this.enableFaceAttribute = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable interaction liveness detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableInteractionLiveness(boolean enable) {
        this.enableInteractionLiveness = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable mask detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableMaskDetect(boolean enable) {
        this.enableMaskDetect = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable face quality detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableFaceQuality(boolean enable) {
        this.enableFaceQuality = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable liveness detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableLiveness(boolean enable) {
        this.enableLiveness = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable face recognition
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableRecognition(boolean enable) {
        this.enableRecognition = enable ? 1 : 0;
        return this;
    }

    /**
     * Enable IR liveness detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableIrLiveness(boolean enable) {
        this.enableIrLiveness = enable ? 1 : 0;
        return this;
    }

    /**
     * Set enable interaction liveness detection
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter setEnableInteractionLiveness(int enable) {
        this.enableInteractionLiveness = enable;
        return this;
    }

    /**
     * Legacy flag retained for source compatibility; 1.2.4 always enables landmarks.
     * @param enable true if enable, false otherwise
     * @return CustomParameter object
     */
    public CustomParameter enableDetectModeLandmark(boolean enable) {
        this.enableDetectModeLandmark = enable ? 1 : 0;
        return this;
    }
}
