package ro.amadya.docs.internal

import org.springframework.core.io.ClassPathResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The OpenAPI document this server was built from (copied into the jar at build time), for the admin API docs page.
 * Admin only: it is a map of every endpoint. Hand-implemented (tag `docs`) because it returns the raw file.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
class ApiDocsController {

    private val spec: ByteArray by lazy { ClassPathResource("openapi/openapi.yaml").inputStream.use { it.readAllBytes() } }

    @GetMapping("/admin/openapi.yaml", produces = ["application/yaml"])
    fun getOpenApiSpec(): ResponseEntity<ByteArray> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/yaml")).body(spec)
}
