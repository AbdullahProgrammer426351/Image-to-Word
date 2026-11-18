# Huawei AppGallery IAP and update APIs.
-dontwarn com.huawei.hianalytics.process.**
-dontwarn com.huawei.hianalytics.util.**
-dontwarn com.huawei.hms.iapfull.**
-dontwarn com.huawei.hms.jos.**
-dontwarn com.huawei.updatesdk.**
-dontwarn com.huawei.android.app.PackageManagerEx
-dontwarn com.huawei.appgallery.log.LogAdaptor
-keep class com.huawei.hms.iap.** { *; }
-keep class com.huawei.hms.iap.entity.** { *; }
-keep class com.huawei.hms.jos.** { *; }
-keep class com.huawei.updatesdk.** { *; }

# Petal Ads dispatches bundled AOSP service APIs through reflection.
-keep class com.huawei.hms.ads.** { *; }
-keep class com.huawei.openalliance.ad.** { *; }
-keep class com.huawei.android.hms.ppskit.** { *; }

# Google Ads / UMP / Billing.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.ump.**
-dontwarn com.android.billingclient.**
