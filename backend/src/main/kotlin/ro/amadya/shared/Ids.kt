package ro.amadya.shared

import java.security.SecureRandom
import java.util.UUID

/** Time-ordered UUIDv7 ids (RFC 9562), generated in the application so aggregates know their id before persisting. */
object Ids {
    private val random = SecureRandom()

    fun newId(): UUID {
        val millis = System.currentTimeMillis()
        val randA = random.nextInt(1 shl 12).toLong()
        val msb = (millis shl 16) or (0x7L shl 12) or randA
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
