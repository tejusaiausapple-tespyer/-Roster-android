# Add project specific ProGuard rules here.
# Firestore model (de)serialization relies on reflection — keep model classes
# once Phase 3/5 introduces them, rather than adding blanket keep rules now.

# WorkManager looks up its internal Room database by name at runtime
# (Class.forName("...WorkDatabase_Impl")) — not covered by WorkManager's own
# consumer rules, so R8 stripped the no-arg constructor and crashed init on
# the very first release build (NoSuchMethodException: WorkDatabase_Impl.<init>).
-keep class androidx.work.impl.WorkDatabase
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# Retrofit + suspend functions: the real response type of a `suspend fun` is read
# reflectively off the generic signature of its Continuation parameter. R8 full-mode
# strips generic signatures from classes it doesn't otherwise keep, which erases that
# type to Object and breaks every suspend WorkerApiService call in release builds
# ("Unable to create converter for class java.lang.Object for method ...") — this is
# Retrofit's own recommended R8 config, not something specific to one endpoint.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.AnnotationAvailable
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# kotlinx.serialization: keep the generated $serializer companions used to (de)serialize
# every @Serializable Worker API request/response model reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.surainvestments.roster.data.remote.**$$serializer { *; }
-keepclassmembers class com.surainvestments.roster.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.surainvestments.roster.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
