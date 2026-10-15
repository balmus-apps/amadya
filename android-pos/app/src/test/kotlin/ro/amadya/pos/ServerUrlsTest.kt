package ro.amadya.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.amadya.pos.ServerUrls.Parsed
import ro.amadya.pos.ServerUrls.Reason

class ServerUrlsTest {

    private fun ok(input: String, allowHttp: Boolean = false) =
        (ServerUrls.parse(input, allowHttp) as Parsed.Ok).server

    private fun error(input: String, allowHttp: Boolean = false) =
        (ServerUrls.parse(input, allowHttp) as Parsed.Error).reason

    @Test
    fun `production address is kept as an origin`() {
        assertEquals("https://amadya.alphatech.ro", ok("https://amadya.alphatech.ro"))
        assertEquals("https://amadya.alphatech.ro", ok("  https://Amadya.AlphaTech.ro/  "))
    }

    @Test
    fun `missing scheme means https`() {
        assertEquals("https://amadya.alphatech.ro", ok("amadya.alphatech.ro"))
    }

    @Test
    fun `a pasted cashier link is reduced to the server`() {
        assertEquals("https://amadya.alphatech.ro", ok("https://amadya.alphatech.ro/cashier/"))
        assertEquals("https://amadya.alphatech.ro", ok("https://amadya.alphatech.ro/cashier"))
    }

    @Test
    fun `default ports are dropped and others kept`() {
        assertEquals("https://example.ro", ok("https://example.ro:443"))
        assertEquals("https://example.ro:8443", ok("https://example.ro:8443"))
        assertEquals("http://192.168.1.10:5178", ok("http://192.168.1.10:5178", allowHttp = true))
        assertEquals("http://192.168.1.10", ok("http://192.168.1.10:80", allowHttp = true))
    }

    @Test
    fun `plain http only when allowed`() {
        assertEquals(Reason.HTTP_NOT_ALLOWED, error("http://192.168.1.10:5178"))
        assertEquals("http://192.168.1.10:5178", ok("http://192.168.1.10:5178", allowHttp = true))
    }

    @Test
    fun `invalid addresses are refused`() {
        assertEquals(Reason.EMPTY, error("   "))
        assertEquals(Reason.INVALID, error("https://example.ro/admin"))
        assertEquals(Reason.INVALID, error("https://example.ro/?x=1"))
        assertEquals(Reason.INVALID, error("https://example.ro/#top"))
        assertEquals(Reason.INVALID, error("https://user@example.ro"))
        assertEquals(Reason.INVALID, error("ftp://example.ro"))
        assertEquals(Reason.INVALID, error("https://exa mple.ro"))
    }

    @Test
    fun `cashier and test urls`() {
        assertEquals("https://example.ro/cashier/", ServerUrls.cashierUrl("https://example.ro"))
        assertEquals("http://10.0.0.5:5178/api/v1/settings/public", ServerUrls.testUrl("http://10.0.0.5:5178"))
    }

    @Test
    fun `same origin check`() {
        val server = "https://amadya.alphatech.ro"
        assertTrue(ServerUrls.isSameOrigin("https://amadya.alphatech.ro/cashier/orders", server))
        assertTrue(ServerUrls.isSameOrigin("https://AMADYA.alphatech.ro:443/cashier/", server))
        assertFalse(ServerUrls.isSameOrigin("http://amadya.alphatech.ro/cashier/", server))
        assertFalse(ServerUrls.isSameOrigin("https://amadya.alphatech.ro:8443/cashier/", server))
        assertFalse(ServerUrls.isSameOrigin("https://evil.example/cashier/", server))
        assertFalse(ServerUrls.isSameOrigin("https://amadya.alphatech.ro.evil.example/", server))
        assertFalse(ServerUrls.isSameOrigin("tel:+40700000000", server))
        assertNull(ServerUrls.originOf("javascript:alert(1)"))
    }

    @Test
    fun `device api answers only the configured origin`() {
        // WebView reports the message's source origin without a path, e.g. "http://192.168.1.10:5178".
        val laptop = "http://192.168.1.10:5178"
        assertTrue(ServerUrls.isSameOrigin("http://192.168.1.10:5178", laptop))
        assertFalse(ServerUrls.isSameOrigin("http://192.168.1.10:8080", laptop))
        assertFalse(ServerUrls.isSameOrigin("http://192.168.1.11:5178", laptop))
        assertFalse(ServerUrls.isSameOrigin("null", laptop))
        assertFalse(ServerUrls.isSameOrigin("", laptop))
    }
}
