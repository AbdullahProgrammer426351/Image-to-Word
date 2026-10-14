package com.image.word.converter.convert.docx.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendJsonParserTest {
    @Test
    fun accountJson_parsesUserCreditsAndSubscription() {
        val result = BackendJsonParser.parseAccountEnvelope(accountEnvelope())

        assertTrue(result is BackendResult.Success)
        val account = (result as BackendResult.Success).value
        assertEquals("backend-user", account.user.id)
        assertEquals("Guest", account.user.userName)
        assertEquals(95L, account.credits.available)
        assertEquals(0L, account.credits.reserved)
        assertEquals("2026-10-17T12:00:00.000Z", account.credits.nextExpiryAt)
        assertEquals("WEEKLY", account.subscription.plan)
        assertEquals("ACTIVE", account.subscription.status)
        assertEquals(true, account.subscription.autoRenewing)
        assertEquals(ConversionAccessMode.CREDIT_BASED, account.conversionAccess.mode)
        assertTrue(account.conversionAccess.canConvert)
    }

    @Test
    fun nullableSubscriptionFields_parseAsNull() {
        val result = BackendJsonParser.parseAccountEnvelope(
            accountEnvelope(
                subscription = """
                    {
                      "plan": null,
                      "status": null,
                      "periodEnd": null,
                      "autoRenewing": null
                    }
                """.trimIndent(),
            ),
        ) as BackendResult.Success

        assertNull(result.value.user.email)
        assertNull(result.value.subscription.plan)
        assertNull(result.value.subscription.status)
        assertNull(result.value.subscription.periodEnd)
        assertNull(result.value.subscription.autoRenewing)
        assertNull(result.value.subscription.acknowledgementState)
    }

    @Test
    fun conversionAccess_parsesFreeAvailabilityAndUnlimitedWithoutCreditsInference() {
        val free = BackendJsonParser.parseAccountEnvelope(
            accountEnvelope(accessMode = "FREE", freeStatus = "AVAILABLE", freeAvailable = true),
        ) as BackendResult.Success
        assertEquals(ConversionAccessMode.FREE, free.value.conversionAccess.mode)
        assertTrue(free.value.conversionAccess.freeConversion.available)

        val unlimited = BackendJsonParser.parseAccountEnvelope(
            accountEnvelope(accessMode = "TEMPORARY_UNLIMITED"),
        ) as BackendResult.Success
        assertEquals(ConversionAccessMode.TEMPORARY_UNLIMITED, unlimited.value.conversionAccess.mode)
        assertTrue(unlimited.value.conversionAccess.canConvert)
    }

    @Test
    fun structuredBackendError_parsesStableCodeAndStatus() {
        val result = BackendJsonParser.parseErrorEnvelope(
            raw = """
                {"success":false,"error":{"code":"ACCOUNT_NOT_FOUND","message":"Missing"}}
            """.trimIndent(),
            httpStatus = 404,
        )

        val failure = result.failure as BackendFailure.Server
        assertEquals("ACCOUNT_NOT_FOUND", failure.error.code)
        assertEquals("Missing", failure.error.message)
        assertEquals(404, failure.error.httpStatus)
    }

    @Test
    fun conversionSuccess_parsesAuthoritativeChargeAndBalance() {
        val result = BackendJsonParser.parseImageConversionEnvelope(
            raw = """
                {
                  "success": true,
                  "result": {"file":"https://provider.example/result.docx"},
                  "conversionId": "conversion-1",
                  "accessMode": "CREDIT_BASED",
                  "credits": {"charged":5,"available":90}
                }
            """.trimIndent(),
            httpStatus = 200,
        ) as BackendResult.Success

        assertEquals("https://provider.example/result.docx", result.value.resultUrl)
        assertEquals("conversion-1", result.value.conversionId)
        assertEquals(5L, result.value.credits.charged)
        assertEquals(90L, result.value.credits.available)
        assertEquals(ConversionAccessMode.CREDIT_BASED, result.value.accessMode)
    }

    @Test
    fun conversionSuccess_preservesFreeAndUnlimitedAccessModesWithZeroCharge() {
        listOf("FREE", "TEMPORARY_UNLIMITED").forEach { mode ->
            val result = BackendJsonParser.parseImageConversionEnvelope(
                raw = """
                    {
                      "success": true,
                      "result": "https://provider.example/$mode.docx",
                      "conversionId": "conversion-$mode",
                      "accessMode": "$mode",
                      "credits": {"charged":0,"available":0}
                    }
                """.trimIndent(),
                httpStatus = 200,
            ) as BackendResult.Success
            assertEquals(ConversionAccessMode.valueOf(mode), result.value.accessMode)
            assertEquals(0L, result.value.credits.charged)
        }
    }

    @Test
    fun insufficientCredits_parsesRequiredAndAvailableWithoutClientCalculation() {
        val result = BackendJsonParser.parseImageConversionEnvelope(
            raw = """
                {
                  "success": false,
                  "error": {"code":"INSUFFICIENT_CREDITS","message":"Insufficient credits"},
                  "conversionId": "conversion-2",
                  "credits": {"required":5,"available":2}
                }
            """.trimIndent(),
            httpStatus = 402,
        ) as BackendResult.Failure

        val error = (result.failure as BackendFailure.Server).error
        assertEquals("INSUFFICIENT_CREDITS", error.code)
        assertEquals("conversion-2", error.conversionId)
        assertEquals(5L, error.requiredCredits)
        assertEquals(2L, error.availableCredits)
    }

    @Test
    fun monthlySubscriptionVerification_parsesAuthoritativeAccountAndCredits() {
        val subscription = """
            {
              "plan": "MONTHLY",
              "status": "ACTIVE",
              "periodEnd": "2026-11-17T12:00:00.000Z",
              "autoRenewing": true,
              "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
            }
        """.trimIndent()
        val result = BackendJsonParser.parseSubscriptionVerificationEnvelope(
            raw = """{"success":true,"account":${accountObject(subscription, 1295)}}""",
            httpStatus = 200,
        ) as BackendResult.Success

        assertEquals("MONTHLY", result.value.subscription.plan)
        assertEquals("ACTIVE", result.value.subscription.status)
        assertEquals(1_295L, result.value.credits.available)
    }

    companion object {
        fun accountObject(
            subscription: String = """
                {
                  "plan": "WEEKLY",
                  "status": "ACTIVE",
                  "periodEnd": "2026-10-17T12:00:00.000Z",
                  "autoRenewing": true,
                  "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
                }
            """.trimIndent(),
            available: Long = 95,
            accessMode: String = "CREDIT_BASED",
            freeStatus: String = "CONSUMED",
            freeAvailable: Boolean = false,
        ): String = """
            {
              "user": {
                "id": "backend-user",
                "userName": "Guest",
                "email": null,
                "accountType": "ANONYMOUS"
              },
              "credits": {
                "available": $available,
                "reserved": 0,
                "nextExpiryAt": "2026-10-17T12:00:00.000Z"
              },
              "subscription": $subscription
              ,"conversionAccess": {
                "mode": "$accessMode",
                "canConvert": ${accessMode != "NONE"},
                "freeConversion": {
                  "status": "$freeStatus",
                  "available": $freeAvailable
                }
              }
            }
        """.trimIndent()

        fun accountEnvelope(
            subscription: String = """
                {
                  "plan": "WEEKLY",
                  "status": "ACTIVE",
                  "periodEnd": "2026-10-17T12:00:00.000Z",
                  "autoRenewing": true,
                  "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
                }
            """.trimIndent(),
            accessMode: String = "CREDIT_BASED",
            freeStatus: String = "CONSUMED",
            freeAvailable: Boolean = false,
        ): String = """{"success":true,"account":${accountObject(
            subscription = subscription,
            accessMode = accessMode,
            freeStatus = freeStatus,
            freeAvailable = freeAvailable,
        )}}"""
    }
}
