# R8 runs as a shrinker only. Obfuscation is off on purpose: the app stores
# enum names (Settings writes Side.name()) and resolves chesslib squares via
# Square.valueOf(), both of which break once constants are renamed. Keeping
# names also leaves stack traces readable without a mapping file.
-dontobfuscate

# ONNX Runtime's native library (libonnxruntime4j_jni.so) calls back into its
# Java classes via JNI. The AAR's own consumer rules only keep the telemetry
# classes, so keep the whole API surface from being shrunk away.
-keep class ai.onnxruntime.** { *; }
