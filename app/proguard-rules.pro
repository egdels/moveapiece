# R8 runs as a shrinker only. Obfuscation is off on purpose: the app stores
# enum names (Settings writes Side.name()) and resolves chesslib squares via
# Square.valueOf(), both of which break once constants are renamed. Keeping
# names also leaves stack traces readable without a mapping file.
-dontobfuscate

# ONNX Runtime's native library (libonnxruntime4j_jni.so) calls back into its
# Java classes via JNI. The AAR's own consumer rules only keep the telemetry
# classes, so keep the whole API surface from being shrunk away.
-keep class ai.onnxruntime.** { *; }

# No optimization, shrinking only. When optimizing, R8 moves static code into
# its record desugaring class com.android.tools.r8.RecordTag: unboxed enum
# methods (PieceType.fromLetter), string-concat outlines and more. Play's
# automatic protection (PairIP) renames that class back to java.lang.Record,
# which ART resolves to the platform class on API 34+, so every call to a
# moved method fails there; the 1.8.0 Play build crashed in
# ChessPosition.fromFen and Move.fromUci. Optimizing saves only ~0.3 MB.
-dontoptimize
