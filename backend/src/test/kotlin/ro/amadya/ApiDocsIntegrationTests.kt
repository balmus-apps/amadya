package ro.amadya

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The admin API docs page reads the server's own OpenAPI document; nobody else may. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class ApiDocsIntegrationTests {

    @LocalServerPort
    private var port: Int = 0
    private val http = HttpClient.newHttpClient()

    private fun spec(token: String?): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/admin/openapi.yaml")).GET()
        token?.let { request.header("Authorization", "Bearer $it") }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    @Test
    fun `the admin gets the exact contract the server was built from`() {
        val response = spec(ApiClient(port).login("admin@test.local", "Admin12345!"))
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").get().startsWith("application/yaml"))
        assertEquals("no-store", response.headers().firstValue("Cache-Control").get())
        assertContentEquals(File("../api/openapi/openapi.yaml").readBytes(), response.body())
    }

    @Test
    fun `other staff and anonymous callers do not`() {
        val api = ApiClient(port)
        val admin = api.login("admin@test.local", "Admin12345!")
        for (role in listOf("MANAGER", "WAITER")) {
            val email = "${role.lowercase()}-docs-${UUID.randomUUID()}@test.local"
            api.post("/api/v1/admin/users", mapOf("email" to email, "name" to role, "password" to "Password123!", "roles" to listOf(role)), admin)
            assertEquals(403, spec(api.login(email, "Password123!")).statusCode(), role)
        }
        assertEquals(401, spec(null).statusCode())
    }
}
