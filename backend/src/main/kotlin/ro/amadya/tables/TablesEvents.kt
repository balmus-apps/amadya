package ro.amadya.tables

import java.math.BigDecimal
import java.time.Instant

/** The waiter asked for the pre-bill (nota de plată) of a table; printed by the printing module. */
data class PreBillRequested(
    val tableLabel: String,
    val waiterName: String?,
    val lines: List<PreBillLine>,
    val total: BigDecimal,
    val paid: BigDecimal,
    val remaining: BigDecimal,
    val currency: String,
    val at: Instant,
)

data class PreBillLine(val name: String, val quantity: Int, val total: BigDecimal)
