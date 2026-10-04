package ro.amadya.shared

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.time.Clock
import java.util.Locale

@Configuration
class WebConfig : WebMvcConfigurer {

    /** All REST controllers of the application live under /api/v1 (the OpenAPI server URL). */
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            "/api/v1",
            HandlerTypePredicate.forBasePackage("ro.amadya").and(HandlerTypePredicate.forAnnotation(RestController::class.java)),
        )
    }

    @Bean
    fun localeResolver(): LocaleResolver = AcceptHeaderLocaleResolver().apply {
        supportedLocales = listOf(Locale.of("ro"), Locale.ENGLISH)
        setDefaultLocale(Locale.of("ro"))
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
