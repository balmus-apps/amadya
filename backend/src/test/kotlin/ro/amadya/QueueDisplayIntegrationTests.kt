package ro.amadya

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Queue display: ready numbers newest first, and they leave the screen after the configured time. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["amadya.ordering.queue-ready-visible=PT4S"],
)
@Import(TestcontainersConfiguration::class, PushTestConfiguration::class)
@ActiveProfiles("test")
class QueueDisplayIntegrationTests {

    @LocalServerPort
    private var port: Int = 0

    private val cocaCola = "0190a000-0000-7000-8000-000000000451" // no station: ready as soon as it is placed

    @Test
    fun `ready orders show newest first and leave the screen after the visible time`() {
        val api = ApiClient(port)
        val admin = api.login("admin@test.local", "Admin12345!")
        fun counterOrder() = api.post("/api/v1/orders", mapOf("channel" to "COUNTER", "lines" to listOf(mapOf("productId" to cocaCola, "quantity" to 1))), admin).text("number")

        val first = counterOrder()
        await atMost Duration.ofSeconds(10) untilAsserted { assertTrue(api.get("/api/v1/queue")["ready"].any { it.asString() == first }) }
        Thread.sleep(1100)
        val second = counterOrder()
        await atMost Duration.ofSeconds(10) untilAsserted { assertTrue(api.get("/api/v1/queue")["ready"].any { it.asString() == second }) }

        val board = api.get("/api/v1/queue")
        assertEquals(4, board["readyVisibleSeconds"].asInt())
        val ready = board["ready"].toList().map { it.asString() }.filter { it == first || it == second }
        assertEquals(listOf(second, first), ready, "most recent first")
        val entry = board["readyOrders"].first { it.path("number").asString() == second }
        assertTrue(OffsetDateTime.parse(entry.path("readyAt").asString()).isAfter(OffsetDateTime.now().minusSeconds(10)))

        // Nobody marked them picked up: they leave the display on their own.
        await atMost Duration.ofSeconds(10) untilAsserted {
            val numbers = api.get("/api/v1/queue")["ready"].toList().map { it.asString() }
            assertTrue(first !in numbers && second !in numbers, numbers.toString())
        }
        // ...while the orders themselves are still waiting to be picked up.
        assertTrue(api.get("/api/v1/orders?status=READY&channel=COUNTER", admin).body.any { it.path("number").asString() == first })
    }
}
