# Ads
-keep class com.google.android.gms.ads.** { *; }

# Billing
-keep class com.android.billingclient.** { *; }

# UMP
-keep class com.google.android.ump.** { *; }

# PDFBox
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }

# Optional safety
-keep class com.airbnb.lottie.** { *; }

# WorkManager
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.background.systemjob.SystemJobService { *; }
-keep class androidx.work.impl.background.systemalarm.SystemAlarmService { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy$* { *; }
#-keep class androidx.work.impl.workers.DiagnosticsReceiver { *; }
-dontwarn androidx.work.impl.**

# Room
#-keep class * extends androidx.room.RoomDatabase
#-keep class * extends androidx.room.Entity
#-keep class * extends androidx.room.Dao
#-dontwarn androidx.room.**

# App Startup
-keep class androidx.startup.** { *; }

# Gemalto (PDFBox dependencies)
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
