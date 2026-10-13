package ro.amadya.media

import java.io.InputStream

/**
 * Staff-only files (scanned supplier documents). They live in the same storage as public media, but the public
 * `/files/{key}` endpoint refuses their keys; they are read only through the module that owns them.
 */
interface PrivateFiles {
    /** Stores the bytes when they are a JPEG, PNG, WebP or PDF (decided from the content), otherwise 422. */
    fun store(bytes: ByteArray): StoredFile

    /** Content type and stream, or null when the key is unknown. */
    fun open(key: String): Pair<String, InputStream>?
}

data class StoredFile(val key: String, val contentType: String)
