package com.image.word.converter.convert.docx.subscriptions

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSubscriptionSafetyTest {
    @Test
    fun v2Verifier_hasNoLocalAcknowledgementOrEntitlementMutation() {
        val source = File(
            "src/main/java/com/image/word/converter/convert/docx/subscriptions/BackendSubscriptionVerifier.kt",
        ).readText()

        assertFalse(source.contains("acknowledgePurchase"))
        assertFalse(source.contains("BillingClient"))
        assertTrue(source.contains("accountGateway.accept"))
        assertTrue(source.contains("accountGateway.refresh"))
    }

    @Test
    fun releaseConfiguration_cannotCallProductionV2() {
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(buildScript.contains("BACKEND_ACCOUNT_INTEGRATION_ENABLED\", \"false"))
        assertTrue(buildScript.contains("BACKEND_FUNCTIONS_BASE_URL\", \"\\\"\\\""))
    }

    @Test
    fun internalConfiguration_isSeparateFromPublicReleaseAndUsesProductionV2Gate() {
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(buildScript.contains("create(\"internal\")"))
        assertTrue(buildScript.contains("BACKEND_ENVIRONMENT\", \"\\\"production-internal\\\""))
        assertTrue(buildScript.contains("BACKEND_ACCOUNT_INTEGRATION_ENABLED\", \"true"))
        assertTrue(buildScript.contains("https://us-central1-converter-api-project.cloudfunctions.net/"))
        assertTrue(buildScript.contains("withBuildType(\"internal\")"))
        assertTrue(File("src/internal/google-services.json").readText().contains(
            "1:1080587695450:android:3dbc635eb260638e852f63",
        ))
    }

    @Test
    fun internalPackageCollisionIsExplicitlyGuardedBeforeTesting() {
        val buildScript = File("build.gradle.kts").readText()
        val internalBlock = buildScript.substringAfter("create(\"internal\")").substringBefore("dependencies {")
        val notes = File("../V2_HARDENING_NOTES.md").readText()

        assertTrue(buildScript.contains("applicationId = \"com.image.word.converter.convert.docx\""))
        assertTrue(buildScript.contains("output.versionCode.set(9)"))
        assertFalse(internalBlock.contains("applicationIdSuffix"))
        assertTrue(notes.contains("shares the published package ID"))
    }

    @Test
    fun diagnosticsDoNotLogAccountBalancesOrPurchaseSecrets() {
        val source = File(
            "src/main/java/com/image/word/converter/convert/docx/ImageToWordApplication.kt",
        ).readText()

        assertFalse(source.contains("availableCredits="))
        assertFalse(source.contains("subscriptionStatus="))
        assertFalse(source.contains("purchaseToken"))
        assertFalse(source.contains("idToken"))
        assertFalse(source.contains("appCheckToken"))
    }

    @Test
    fun oneTimeCreditFlow_usesInAppCatalogAndNeverConsumesOrGrantsLocally() {
        val viewModel = File(
            "src/main/java/com/image/word/converter/convert/docx/subscriptions/SubscriptionViewModel.kt",
        ).readText()
        val verifier = File(
            "src/main/java/com/image/word/converter/convert/docx/subscriptions/CreditPurchaseVerifier.kt",
        ).readText()

        assertTrue(viewModel.contains("BillingClient.ProductType.INAPP"))
        assertTrue(viewModel.contains("getCreditProductCatalog"))
        assertFalse(viewModel.contains("consumeAsync"))
        assertFalse(verifier.contains("consumePurchase"))
        assertFalse(verifier.contains("acknowledgePurchase"))
        assertTrue(verifier.contains("accountGateway.accept"))
    }

    @Test
    fun purchaseRestoreAndBackgroundVerification_suppressAds() {
        val viewModel = File(
            "src/main/java/com/image/word/converter/convert/docx/subscriptions/SubscriptionViewModel.kt",
        ).readText()
        val retryService = File(
            "src/main/java/com/image/word/converter/convert/docx/subscriptions/SubscriptionVerificationRetryService.kt",
        ).readText()

        assertTrue(viewModel.contains("withAdsSuppressed { queryActivePurchases(fromRestoreAction = true) }"))
        assertTrue(viewModel.contains("private suspend fun <T> withAdsSuppressed"))
        assertTrue(retryService.contains("AdsGate.beginCriticalOperation()"))
        assertTrue(retryService.contains("AdsGate.endCriticalOperation()"))
    }

    @Test
    fun internalPlayPackaging_reportsPublishedVersionCollision() {
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(buildScript.contains("checkInternalPlayVersionCode"))
        assertTrue(buildScript.contains("publishedVersionCode = 11"))
        assertTrue(buildScript.contains("dependsOn(checkInternalPlayVersionCode)"))
    }
}
