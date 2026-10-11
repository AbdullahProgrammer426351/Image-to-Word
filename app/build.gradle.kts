plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.image.word.converter.convert.docx"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.image.word.converter.convert.docx"
        minSdk = 24
        targetSdk = 37
        versionCode = 8
        versionName = "1.07"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "BACKEND_ENVIRONMENT", "\"emulator\"")
            buildConfigField("boolean", "BACKEND_ACCOUNT_INTEGRATION_ENABLED", "true")
            buildConfigField("String", "BACKEND_FIREBASE_PROJECT_ID", "\"demo-converter-credit\"")
            buildConfigField(
                "String",
                "BACKEND_FUNCTIONS_BASE_URL",
                "\"http://10.0.2.2:5001/demo-converter-credit/us-central1/\"",
            )
            buildConfigField("String", "BACKEND_AUTH_EMULATOR_HOST", "\"10.0.2.2\"")
            buildConfigField("int", "BACKEND_AUTH_EMULATOR_PORT", "9099")
            buildConfigField("boolean", "ISOLATE_PRODUCTION_SERVICES", "true")
//            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BACKEND_ENVIRONMENT", "\"production-disabled\"")
            buildConfigField("boolean", "BACKEND_ACCOUNT_INTEGRATION_ENABLED", "false")
            buildConfigField("String", "BACKEND_FIREBASE_PROJECT_ID", "\"converter-api-project\"")
            buildConfigField("String", "BACKEND_FUNCTIONS_BASE_URL", "\"\"")
            buildConfigField("String", "BACKEND_AUTH_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "BACKEND_AUTH_EMULATOR_PORT", "0")
            buildConfigField("boolean", "ISOLATE_PRODUCTION_SERVICES", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.firebase.analytics)

    // Third-party: Firebase Auth
    implementation(libs.firebase.auth)
    // Third-party: Firebase Remote Config
    implementation(libs.firebase.config)
    // Third-party: Firebase Crashlytics
    implementation(libs.firebase.crashlytics)
    // Third-party: Coroutines bridge for Google Tasks (Firebase Auth token await)
    implementation(libs.kotlinx.coroutines.play.services)
    // Third-party: Coil Compose image loading
    implementation(libs.coil.compose)
    // Third-party: Coil SVG decoder support
    implementation(libs.coil.svg)
    // Third-party: Google Mobile Ads SDK
    implementation(libs.google.play.services.ads)
    // Third-party: Google Play Billing SDK
    implementation(libs.google.play.billing.ktx)
    // Third-party: Google UMP consent SDK
    implementation(libs.google.ump)
    // Third-party: Airbnb Lottie animations
    implementation(libs.lottie.compose)
    // Third-party: Google ML Kit Text Recognition
    implementation(libs.mlkit.text.recognition)
    // Third-party: Google ML Kit Barcode/QR Scanning
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.play.services.mlkit.text.recognition.common)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // re-order list
    implementation(libs.reorderable)
    // cropping library
    implementation(libs.yalantis.ucrop)
    // color picker
    implementation(libs.skydoves.colorpicker.compose)
}
