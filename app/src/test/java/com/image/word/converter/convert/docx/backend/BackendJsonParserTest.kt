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

    companion object {
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
        ): String = """
            {
              "success": true,
              "account": {
                "user": {
                  "id": "backend-user",
                  "userName": "Guest",
                  "email": null,
                  "accountType": "ANONYMOUS"
                },
                "credits": {
                  "available": 95,
                  "reserved": 0,
                  "nextExpiryAt": "2026-10-17T12:00:00.000Z"
                },
                "subscription": $subscription
              }
            }
        """.trimIndent()
    }
}
