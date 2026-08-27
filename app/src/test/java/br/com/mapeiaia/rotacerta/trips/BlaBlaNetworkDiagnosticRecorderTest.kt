package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaNetworkDiagnosticRecorderTest {
    @Test
    fun bridgeAcceptsOnlyTheExactBlaBlaCarPageOrigin() {
        assertTrue(BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin("https://www.blablacar.com.br"))
        assertTrue(BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin("https://www.blablacar.com.br/"))
        assertFalse(BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin("http://www.blablacar.com.br"))
        assertFalse(BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin("https://evil.blablacar.com.br"))
        assertFalse(BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin("https://www.blablacar.com.br.evil.test"))
    }

    @Test
    fun nativePassRemovesQueriesSecretsAndPersonalResponseValues() {
        val raw = """
            {
              "schema": 1,
              "transport": "fetch",
              "method": "POST",
              "endpoint": "https://api.blablacar.com/api/v3/rides/123456?access_token=raw-query-secret",
              "page": "https://www.blablacar.com.br/rides/offer/edit/card-real/options?session=raw-page-secret",
              "status": 200,
              "durationBucketMs": 250,
              "contentKind": "json",
              "body": {
                "kind": "object",
                "fields": {
                  "name": "Maria de Souza",
                  "phone": "+55 11 99999-0000",
                  "authorization": "Bearer raw-header-secret",
                  "route": "Rua Particular, 123",
                  "seats": 3,
                  "nested": { "token": "raw-body-secret" }
                }
              }
            }
        """.trimIndent()

        val sanitized = BlaBlaNetworkDiagnosticPolicy.anonymizeBridgePayload(raw, "test-only-random-salt")

        assertNotNull(sanitized)
        val value = sanitized.orEmpty()
        assertFalse(value.contains("raw-query-secret"))
        assertFalse(value.contains("raw-page-secret"))
        assertFalse(value.contains("raw-header-secret"))
        assertFalse(value.contains("raw-body-secret"))
        assertFalse(value.contains("Maria de Souza"))
        assertFalse(value.contains("99999-0000"))
        assertFalse(value.contains("Rua Particular"))
        assertFalse(value.contains("access_token"))
        assertTrue(value.contains("https://api.blablacar.com/api/v3/rides/:id_"))
        assertTrue(value.contains("https://www.blablacar.com.br/rides/offer/edit/:id_"))
        assertTrue(value.contains("\"seats\":3"))
        assertTrue(value.contains("\"kind\":\"redacted\""))
    }

    @Test
    fun payloadFromNonBlaBlaCarEndpointIsRejected() {
        val raw = """
            {
              "schema": 1,
              "transport": "xhr",
              "method": "GET",
              "endpoint": "https://blablacar.com.evil.test/steal?token=secret",
              "page": "https://www.blablacar.com.br/rides/offer/card",
              "status": 200,
              "durationBucketMs": 50,
              "contentKind": "json",
              "body": {"kind":"object"}
            }
        """.trimIndent()

        assertNull(BlaBlaNetworkDiagnosticPolicy.anonymizeBridgePayload(raw, "salt"))
    }

    @Test
    fun opaqueTagsAreSessionScoped() {
        val first = BlaBlaNetworkDiagnosticPolicy.opaqueTag("real-card-id", "salt-a")
        val sameSession = BlaBlaNetworkDiagnosticPolicy.opaqueTag("real-card-id", "salt-a")
        val nextSession = BlaBlaNetworkDiagnosticPolicy.opaqueTag("real-card-id", "salt-b")

        assertEquals(first, sameSession)
        assertNotEquals(first, nextSession)
        assertFalse(first.contains("real-card-id"))
    }

    @Test
    fun documentStartObserverIsNetworkOnlyAndDoesNotInspectRequestSecrets() {
        val script = BlaBlaNetworkDiagnosticPolicy.DOCUMENT_START_SCRIPT

        assertTrue(script.contains("window.fetch"))
        assertTrue(script.contains("XMLHttpRequest.prototype.open"))
        assertTrue(script.contains("XMLHttpRequest.prototype.send"))
        assertTrue(script.contains("bridge.postMessage"))
        assertTrue(script.contains("__rotaCertaNetworkTripSource"))
        assertTrue(script.contains("rememberNetworkTripSources(parsed)"))
        assertTrue(script.contains("trip_offer_encrypted_id"))
        assertTrue(script.contains("root.waypoints"))
        assertTrue(script.contains("waypointsComplete"))
        assertTrue(script.contains("pickup_waypoint"))
        assertTrue(script.contains("dropoff_waypoint"))
        assertEquals(1, script.windowCount("window.fetch = function"))
        assertEquals(1, script.windowCount("XMLHttpRequest.prototype.send = function"))
        assertFalse(script.contains("document."))
        assertFalse(script.contains("querySelector"))
        assertFalse(script.contains("localStorage"))
        assertFalse(script.contains("sessionStorage"))
        assertFalse(script.contains("document.cookie"))
        assertFalse(script.contains("getAllResponseHeaders"))
        assertFalse(script.contains("request.headers"))
        assertFalse(script.contains("args[1].body"))
    }

    private fun String.windowCount(needle: String): Int = windowed(needle.length)
        .count { value -> value == needle }
}
