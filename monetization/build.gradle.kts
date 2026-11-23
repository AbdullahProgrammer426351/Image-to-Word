plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.espressotech.monetization"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    flavorDimensions += "store"

    productFlavors {
        create("google")
        create("huawei")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.config)

    "googleImplementation"(libs.google.play.billing.ktx)
    "googleImplementation"(libs.google.play.services.ads)
    "googleImplementation"(libs.google.meta.mediation)
    "googleImplementation"(libs.yandex.mobile.ads)
    "googleImplementation"(libs.google.ump)

    "huaweiImplementation"(libs.huawei.iap)
    "huaweiImplementation"(libs.huawei.appservice)
    "huaweiImplementation"(libs.huawei.base)
    "huaweiImplementation"(libs.huawei.hmscoreinstaller)
    "huaweiImplementation"(libs.huawei.ads)

    testImplementation(libs.junit)
}
