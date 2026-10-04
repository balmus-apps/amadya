package ro.amadya

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Minimal JSON-over-HTTP client for black-box API tests against the running server. */
class ApiClient(private val port: Int) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = JsonMapper.builder().build()

    data class Response(val status: Int, val body: JsonNode) {
        operator fun get(field: String): JsonNode = body.path(field)
        fun text(field: String): String = body.path(field).asString()
    }

    fun get(path: String, token: String? = null, lang: String? = null) = send("GET", path, null, token, lang)
    fun post(path: String, body: Any? = null, token: String? = null, lang: String? = null) = send("POST", path, body, token, lang)
    fun put(path: String, body: Any, token: String? = null) = send("PUT", path, body, token, null)
    fun patch(path: String, body: Any, token: String? = null) = send("PATCH", path, body, token, null)
    fun delete(path: String, token: String? = null) = send("DELETE", path, null, token, null)

    fun login(email: String, password: String): String =
        post("/api/v1/auth/login", mapOf("email" to email, "password" to password)).also { check(it.status == 200) { "login failed: ${it.body}" } }
            .text("accessToken")

    /** Opens an SSE stream and returns the first `count` non-empty lines. */
    fun sseLines(path: String, count: Int): List<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Accept", "text/event-stream").GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofLines())
        return response.body().filter { it.isNotBlank() }.limit(count.toLong()).toList()
    }

    private fun send(method: String, path: String, body: Any?, token: String?, lang: String?): Response {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(20))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Accept", "application/json, application/problem+json")
        if (body != null) builder.header("Content-Type", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        lang?.let { builder.header("Accept-Language", it) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val parsed = response.body().takeIf { it.isNotBlank() }?.let { json.readTree(it) } ?: json.createObjectNode()
        return Response(response.statusCode(), parsed)
    }
}
