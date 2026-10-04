package ro.amadya.shared

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.ZoneId

@ConfigurationProperties("amadya")
data class AmadyaProperties(
    val timezone: ZoneId = ZoneId.of("Europe/Bucharest"),
    val publicBaseUrl: String = "http://localhost",
    val cors: Cors = Cors(),
    val security: Security = Security(),
) {
    data class Cors(val allowedOrigins: List<String> = emptyList())

    data class Security(
        val jwtSecret: String = "",
        val allowGeneratedSecret: Boolean = false,
        val accessTokenTtl: Duration = Duration.ofMinutes(15),
        val refreshTokenTtl: Duration = Duration.ofDays(30),
    )
}
