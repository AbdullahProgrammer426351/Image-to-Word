plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

val physicalDeviceIntegrationTest =
    providers.gradleProperty("physicalDeviceIntegrationTest").orNull == "true"

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
            if (physicalDeviceIntegrationTest) {
                applicationIdSuffix = ".integrationtest"
                resValue("string", "google_app_id", "1:000000000000:android:demo-converter-credit")
                resValue("string", "google_api_key", "demo-converter-credit")
                resValue("string", "project_id", "demo-converter-credit")
                resValue("string", "gcm_defaultSenderId", "000000000000")
            }
            buildConfigField("String", "BACKEND_ENVIRONMENT", "\"emulator\"")
            buildConfigField("boolean", "BACKEND_ACCOUNT_INTEGRATION_ENABLED", "true")
            buildConfigField("String", "BACKEND_FIREBASE_PROJECT_ID", "\"demo-converter-credit\"")
            buildConfigField(
                "String",
                "BACKEND_FIREBASE_APP_ID",
                "\"1:000000000000:android:demo-converter-credit\"",
            )
            buildConfigField(
                "String",
                "BACKEND_FUNCTIONS_BASE_URL",
                if (physicalDeviceIntegrationTest) {
                    "\"http://127.0.0.1:5001/demo-converter-credit/us-central1/\""
                } else {
                    "\"http://10.0.2.2:5001/demo-converter-credit/us-central1/\""
                },
            )
            buildConfigField(
                "String",
                "BACKEND_AUTH_EMULATOR_HOST",
                if (physicalDeviceIntegrationTest) "\"127.0.0.1\"" else "\"10.0.2.2\"",
            )
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
            buildConfigField(
                "String",
                "BACKEND_FIREBASE_APP_ID",
                "\"1:1080587695450:android:3dbc635eb260638e852f63\"",
            )
            buildConfigField("String", "BACKEND_FUNCTIONS_BASE_URL", "\"\"")
            buildConfigField("String", "BACKEND_AUTH_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "BACKEND_AUTH_EMULATOR_PORT", "0")
            buildConfigField("boolean", "ISOLATE_PRODUCTION_SERVICES", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("internal") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("String", "BACKEND_ENVIRONMENT", "\"production-internal\"")
            buildConfigField("boolean", "BACKEND_ACCOUNT_INTEGRATION_ENABLED", "true")
            buildConfigField("String", "BACKEND_FIREBASE_PROJECT_ID", "\"converter-api-project\"")
            buildConfigField(
                "String",
                "BACKEND_FIREBASE_APP_ID",
                "\"1:1080587695450:android:3dbc635eb260638e852f63\"",
            )
            buildConfigField(
                "String",
                "BACKEND_FUNCTIONS_BASE_URL",
                "\"https://us-central1-converter-api-project.cloudfunctions.net/\"",
            )
            buildConfigField("String", "BACKEND_AUTH_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "BACKEND_AUTH_EMULATOR_PORT", "0")
            buildConfigField("boolean", "ISOLATE_PRODUCTION_SERVICES", "false")
        }
    }

    androidComponents {
        onVariants(selector().withBuildType("internal")) { variant ->
            variant.outputs.forEach { output -> output.versionCode.set(9) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

if (physicalDeviceIntegrationTest) {
    tasks.matching { it.name == "processDebugGoogleServices" }.configureEach {
        enabled = false
    }
    // A prior normal debug build may leave production google-services resources behind.
    // Remove only that generated directory so the emulator resources below remain authoritative.
    tasks.matching { it.name == "packageDebugResources" }.configureEach {
        doFirst {
            layout.buildDirectory.dir("generated/res/processDebugGoogleServices").get().asFile.deleteRecursively()
        }
    }
}

// Internal Play validation must not upload Crashlytics symbols from a local build.
// Production release behavior remains unchanged and its normal release task stays enabled.
tasks.matching { it.name == "uploadCrashlyticsMappingFileInternal" }.configureEach {
    enabled = false
}

val checkInternalPlayVersionCode by tasks.registering {
    group = "verification"
    description = "Fails before packaging an internal Play build whose version code cannot update production."
    doLast {
        val configuredInternalVersionCode = 9
        val publishedVersionCode = 11
        check(configuredInternalVersionCode > publishedVersionCode) {
            "Internal Play build versionCode $configuredInternalVersionCode must be greater than " +
                "published versionCode $publishedVersionCode before upload."
        }
    }
}

tasks.matching { it.name in setOf("bundleInternal", "packageInternal") }.configureEach {
    dependsOn(checkInternalPlayVersionCode)
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
    // Third-party: Firebase App Check with the production Play Integrity provider
    implementation(libs.firebase.appcheck.playintegrity)
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
