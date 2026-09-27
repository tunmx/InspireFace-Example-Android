package com.insightface.sdk.inspireface.base;

/** Owned token payload copied by InspireFace 1.2.4's ExecuteFaceTrack JNI. */
public class FaceBasicToken {
    // JNI looks up these exact field names and types when producing or consuming tokens.
    public byte[] data;
    public int size;

    public FaceBasicToken() {}
}
