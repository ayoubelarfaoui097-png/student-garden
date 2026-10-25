# Kotlin serialization supplies its own consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
# NewPipe's JavaScript utility explicitly enables interpreted mode. Rhino's
# unused desktop optimizer references JDK Dynalink, which Android does not ship.
-dontwarn jdk.dynalink.**
# Java bean conversion is unused: videos evaluate JavaScript, not Java objects.
-dontwarn java.beans.**
# Protobuf Lite serializes NewPipe playlist continuations using reflected field
# names. Without this rule optimized builds fail before requesting the playlist.
# https://github.com/protocolbuffers/protobuf/blob/main/java/lite.md
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
# NewPipe loads localized date parsers by name.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
