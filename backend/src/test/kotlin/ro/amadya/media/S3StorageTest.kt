package ro.amadya.media

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import ro.amadya.media.internal.MediaProperties
import ro.amadya.media.internal.S3Storage
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** The S3 adapter against RustFS, an open-source S3-compatible server (MinIO no longer publishes free images). */
class S3StorageTest {
    companion object {
        private val s3 = GenericContainer("rustfs/rustfs:latest")
            .withExposedPorts(9000)
            .withEnv("RUSTFS_ACCESS_KEY", "amadya")
            .withEnv("RUSTFS_SECRET_KEY", "amadya-secret-123")
            .waitingFor(Wait.forListeningPort())

        @JvmStatic @BeforeAll fun start() = s3.start()

        @JvmStatic @AfterAll fun stop() = s3.stop()
    }

    @Test
    fun `stores and reads objects, missing keys are null`() {
        val storage = S3Storage(
            MediaProperties(provider = "s3", endpoint = "http://${s3.host}:${s3.getMappedPort(9000)}", accessKey = "amadya", secretKey = "amadya-secret-123", bucket = "amadya-test"),
        )
        val bytes = ByteArray(1024) { (it % 251).toByte() }
        storage.put("a.png", bytes, "image/png")
        assertContentEquals(bytes, storage.get("a.png")!!.readAllBytes())
        assertNull(storage.get("missing.png"))
    }
}
