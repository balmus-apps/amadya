package ro.amadya.shared

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless JWT (HS256) security. Public endpoints are listed explicitly; everything else needs a token.
 * Fine-grained role checks live on the controllers (@PreAuthorize).
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig(private val props: AmadyaProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jwtSecretKey(): SecretKey {
        val configured = props.security.jwtSecret.takeUnless { it.contains("CHANGE_ME") }.orEmpty()
        val bytes = when {
            configured.length >= 32 -> configured.toByteArray()
            props.security.allowGeneratedSecret -> {
                log.warn("JWT_SECRET not set: using a random secret, tokens will not survive a restart (dev only)")
                ByteArray(32).also { SecureRandom().nextBytes(it) }
            }
            else -> error("amadya.security.jwt-secret (JWT_SECRET) must be at least 32 characters")
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun jwtDecoder(key: SecretKey): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        val jwtConverter = JwtAuthenticationConverter().apply { setJwtGrantedAuthoritiesConverter(authorities) }

        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/settings/public", "/api/v1/menu", "/api/v1/queue", "/api/v1/queue/events",
                    "/api/v1/orders/*", "/api/v1/orders/*/events",
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                    "/api/v1/orders", "/api/v1/orders/*/payment-intent", "/api/v1/orders/*/push-subscriptions",
                    "/api/v1/payments/webhooks/**", "/api/v1/payments/*/simulate-capture",
                ).permitAll()
                it.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                it.requestMatchers("/api/v1/admin/**").hasAnyRole(Roles.ADMIN, Roles.MANAGER)
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtConverter) } }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> writeProblem(response, HttpStatus.UNAUTHORIZED, "error.unauthorized") }
                it.accessDeniedHandler { _, response, _ -> writeProblem(response, HttpStatus.FORBIDDEN, "error.forbidden") }
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = props.cors.allowedOrigins.filter { it.isNotBlank() }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Accept-Language", "Idempotency-Key", "Cache-Control", "Last-Event-ID", "X-Requested-With")
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", config) }
    }

    private fun writeProblem(response: jakarta.servlet.http.HttpServletResponse, status: HttpStatus, code: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write("""{"type":"about:blank","title":"${status.reasonPhrase}","status":${status.value()},"code":"$code"}""")
    }
}
