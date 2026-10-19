# ONNX JNI resolves Java types/members by name; upstream requires this for R8.
# https://onnxruntime.ai/docs/build/android.html#note-proguard-rules-for-r8-minimization-android-app-builds-to-work
-keep class ai.onnxruntime.** { *; }

# WorkManager persists these names, including jobs scheduled by older versions.
-keep class org.yanjing.app.update.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room 2.6 creates this generated database through getDeclaredConstructor().
# Keeping its class name alone does not preserve the constructor in R8 full mode.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
