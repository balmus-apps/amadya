package ro.amadya.media.internal

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.errors.ErrorResponseException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ro.amadya.contract.api.MediaApi
import ro.amadya.contract.model.UploadedFile
import ro.amadya.shared.Ids
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("amadya.media")
data class MediaProperties(
    /** `filesystem` (default, a Docker volume) or `s3` (any S3-compatible service: AWS S3, Cloudflare R2, Garage, RustFS...). */
    val provider: String = "filesystem",
    val dir: String = "./data/files",
    val endpoint: String = "",
    val region: String = "us-east-1",
    val accessKey: String = "",
    val secretKey: String = "",
    val bucket: String = "amadya",
    val maxBytes: Long = 5 * 1024 * 1024,
)

/** Port for binary storage (ADR 0008). Keys are random and immutable. */
interface FileStorage {
    fun put(key: String, bytes: ByteArray, contentType: String)

    /** Returns the content, or null when the key does not exist. */
    fun get(key: String): InputStream?
}

@Component
@ConditionalOnProperty(name = ["amadya.media.provider"], havingValue = "filesystem", matchIfMissing = true)
class FilesystemStorage(props: MediaProperties) : FileStorage {
    private val root: Path = Path.of(props.dir).toAbsolutePath().normalize()

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        Files.createDirectories(root)
        Files.write(resolve(key), bytes)
    }

    override fun get(key: String): InputStream? = resolve(key).takeIf(Files::isRegularFile)?.let(Files::newInputStream)

    private fun resolve(key: String): Path = root.resolve(key).normalize().also { require(it.parent == root) { "invalid key" } }
}

@Component
@ConditionalOnProperty(name = ["amadya.media.provider"], havingValue = "s3")
class S3Storage(private val props: MediaProperties) : FileStorage {
    private val client: MinioClient by lazy {
        MinioClient.builder().endpoint(props.endpoint).region(props.region).credentials(props.accessKey, props.secretKey).build()
    }

    @Volatile private var bucketReady = false

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        if (!bucketReady) {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket).build())
            bucketReady = true
        }
        client.putObject(PutObjectArgs.builder().bucket(props.bucket).`object`(key).stream(bytes.inputStream(), bytes.size.toLong(), -1).contentType(contentType).build())
    }

    override fun get(key: String): InputStream? = try {
        client.getObject(GetObjectArgs.builder().bucket(props.bucket).`object`(key).build())
    } catch (e: ErrorResponseException) {
        if (e.errorResponse().code() == "NoSuchKey") null else throw e
    }
}

@Service
class MediaService(private val props: MediaProperties, private val storage: FileStorage) {

    fun store(file: MultipartFile): UploadedFile {
        if (file.isEmpty || file.size > props.maxBytes) throw UnprocessableException("media.invalid_size", props.maxBytes / 1024 / 1024)
        val bytes = file.bytes
        // Decide the type from the content itself, never from the client's header or file name.
        val (type, ext) = sniff(bytes) ?: throw UnprocessableException("media.unsupported_type")
        val key = "${Ids.newId()}.$ext"
        storage.put(key, bytes, type)
        return UploadedFile(key = key, url = "/api/v1/files/$key", contentType = type, bytes = bytes.size.toLong())
    }

    fun open(key: String): Pair<String, InputStream> {
        val type = types[key.substringAfterLast('.', "")] ?: throw NotFoundException("File")
        return type to (storage.get(key) ?: throw NotFoundException("File"))
    }

    companion object {
        private val types = mapOf("jpg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp")

        fun sniff(b: ByteArray): Pair<String, String>? = when {
            b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "image/jpeg" to "jpg"
            b.size > 8 && b.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png" to "png"
            b.size > 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP" -> "image/webp" to "webp"
            else -> null
        }
    }
}

@RestController
class MediaController(private val media: MediaService) : MediaApi {

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    override fun uploadFile(file: MultipartFile): ResponseEntity<UploadedFile> = ResponseEntity.status(HttpStatus.CREATED).body(media.store(file))

    override fun getFile(key: String): ResponseEntity<Resource> {
        val (type, stream) = media.open(key)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(InputStreamResource(stream))
    }
}
