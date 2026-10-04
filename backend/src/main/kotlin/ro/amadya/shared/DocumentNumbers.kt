package ro.amadya.shared

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** Gap-free yearly numbering for accounting documents: NIR-2026-000001, TR-2026-000004, ... */
@Component
class DocumentNumbers(private val jdbc: JdbcClient) {
    fun next(prefix: String, year: Int): String {
        val value = jdbc.sql(
            """INSERT INTO document_counter (prefix, year, last_value) VALUES (:p, :y, 1)
               ON CONFLICT (prefix, year) DO UPDATE SET last_value = document_counter.last_value + 1
               RETURNING last_value""",
        ).param("p", prefix).param("y", year).query(Int::class.java).single()
        return "%s-%d-%06d".format(prefix, year, value)
    }
}
