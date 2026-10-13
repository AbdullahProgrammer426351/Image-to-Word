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
}
