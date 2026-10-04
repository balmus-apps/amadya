package ro.amadya

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Promotions, image upload (MinIO) and the dashboard report. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class AdminFeaturesIntegrationTests {

    @LocalServerPort
    private var port: Int = 0
    private lateinit var api: ApiClient
    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        api = ApiClient(port)
    }

    private fun upload(token: String, bytes: ByteArray, filename: String): HttpResponse<String> {
        val boundary = "----amadya${UUID.randomUUID()}"
        val body = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray() +
            bytes + "\r\n--$boundary--\r\n".toByteArray()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/files"))
            .header("Authorization", "Bearer $token").header("Content-Type", "multipart/form-data; boundary=$boundary")
            .header("Accept", "application/json, application/problem+json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `images are validated by content, stored and served publicly`() {
        val token = api.login("admin@test.local", "Admin12345!")
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { it.toByte() }

        val fake = upload(token, "<script>alert(1)</script>".toByteArray(), "evil.png")
        assertEquals(422, fake.statusCode(), fake.body())

        val ok = upload(token, png, "burger.png")
        assertEquals(201, ok.statusCode(), ok.body())
        val url = Regex("\"url\":\"([^\"]+)\"").find(ok.body())!!.groupValues[1]
        assertTrue(url.matches(Regex("/api/v1/files/[0-9a-f-]{36}\\.png")))

        val served = http.send(HttpRequest.newBuilder(URI("http://localhost:$port$url")).build(), HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, served.statusCode())
        assertEquals("image/png", served.headers().firstValue("Content-Type").get())
        assertTrue(served.body().contentEquals(png))
        assertTrue(served.headers().firstValue("Cache-Control").get().contains("immutable"))
        assertEquals(401, upload("bad-token", png, "x.png").statusCode())
    }

    @Test
    fun `promotions are managed by admins and shown on the menu while active`() {
        val token = api.login("admin@test.local", "Admin12345!")
        val created = api.post(
            "/api/v1/admin/promotions",
            mapOf("title" to mapOf("ro" to "Happy hour", "en" to "Happy hour"), "badge" to "-20%", "productId" to "0190a000-0000-7000-8000-000000000441", "sortOrder" to 99),
            token,
        )
        assertEquals(201, created.status, created.body.toString())
        assertTrue(api.get("/api/v1/menu")["promotions"].any { it.path("badge").asString() == "-20%" })

        val id = created.text("id")
        api.put("/api/v1/admin/promotions/$id", mapOf("title" to mapOf("ro" to "Happy hour"), "badge" to "-20%", "active" to false), token)
        assertTrue(api.get("/api/v1/menu")["promotions"].none { it.path("badge").asString() == "-20%" })

        assertEquals(422, api.put("/api/v1/admin/promotions/$id", mapOf("title" to mapOf("ro" to "X"), "startsAt" to "2026-10-10T10:00:00Z", "endsAt" to "2026-10-09T10:00:00Z"), token).status)
        assertEquals(204, api.delete("/api/v1/admin/promotions/$id", token).status)
    }

    @Test
    fun `dashboard reports sales of the period`() {
        val token = api.login("admin@test.local", "Admin12345!")
        val cashier = api.post("/api/v1/admin/users", mapOf("email" to "d-${UUID.randomUUID()}@test.local", "name" to "D", "password" to "Password123!", "roles" to listOf("CASHIER")), token)
            .let { api.login(it.text("email"), "Password123!") }
        val today = LocalDate.now(java.time.ZoneId.of("Europe/Bucharest"))
        val before = api.get("/api/v1/admin/reports/dashboard?from=$today&to=$today", token)
        api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to "0190a000-0000-7000-8000-000000000452", "quantity" to 3))), cashier)

        val after = api.get("/api/v1/admin/reports/dashboard?from=$today&to=$today", token)
        assertEquals(200, after.status, after.body.toString())
        assertEquals(before["orders"].asInt() + 1, after["orders"].asInt())
        assertEquals(
            java.math.BigDecimal(before["revenue"].path("amount").asString()) + java.math.BigDecimal("18.00"),
            java.math.BigDecimal(after["revenue"].path("amount").asString()),
        )
        assertTrue(after["topProducts"].any { it.path("name").asString() == "Apă plată 500 ml" })
        assertEquals(422, api.get("/api/v1/admin/reports/dashboard?from=$today&to=${today.minusDays(1)}", token).status)
        assertEquals(403, api.get("/api/v1/admin/reports/dashboard?from=$today&to=$today", cashier).status)
    }
}
