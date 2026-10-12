package ro.amadya.media.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/**
 * DEV ONLY: copies the demo menu photos (classpath demo-media/, free Pexels photos) into media storage, so the
 * dev-seeded products and promotions that point at /api/v1/files/<product id>.jpg have their images. Files already present
 * are left alone, so a restart costs nothing and a photo replaced from the admin is never overwritten.
 */
@Component
@Profile("dev")
class DemoMediaLoader(private val storage: FileStorage) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun load() {
        val photos = PathMatchingResourcePatternResolver().getResources("classpath:demo-media/*.jpg")
        var copied = 0
        try {
            photos.forEach { photo ->
                val key = photo.filename ?: return@forEach
                if (storage.get(key)?.use { true } == true) return@forEach
                storage.put(key, photo.inputStream.use { it.readAllBytes() }, "image/jpeg")
                copied++
            }
        } catch (e: Exception) {
            // Demo photos are a nicety: a storage problem must not keep the app from starting.
            log.warn("Demo menu photos not copied into media storage: {}", e.toString())
        }
        if (copied > 0) log.info("Demo menu: {} photo(s) copied into media storage", copied)
    }
}
