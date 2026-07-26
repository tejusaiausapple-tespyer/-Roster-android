# Add project specific ProGuard rules here.
# Firestore model (de)serialization relies on reflection — keep model classes
# once Phase 3/5 introduces them, rather than adding blanket keep rules now.

# WorkManager looks up its internal Room database by name at runtime
# (Class.forName("...WorkDatabase_Impl")) — not covered by WorkManager's own
# consumer rules, so R8 stripped the no-arg constructor and crashed init on
# the very first release build (NoSuchMethodException: WorkDatabase_Impl.<init>).
-keep class androidx.work.impl.WorkDatabase
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
