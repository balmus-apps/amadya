package ro.amadya.reporting.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.ReportingApi
import ro.amadya.contract.model.Dashboard
import ro.amadya.contract.model.DashboardByChannelInner
import ro.amadya.contract.model.DashboardByDayInner
import ro.amadya.contract.model.DashboardByHourInner
import ro.amadya.contract.model.DashboardTopProductsInner
import ro.amadya.contract.model.OrderChannel
import ro.amadya.settings.RestaurantSettingsApi
import ro.amadya.shared.AmadyaProperties
import ro.amadya.shared.UnprocessableException
import ro.amadya.shared.localized
import ro.amadya.shared.toMoney
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/**
 * Read-only KPIs. Reporting reads other modules' tables directly through SQL (a read model, ADR 0001);
 * it never writes and no other module depends on it.
 */
@Service
@Transactional(readOnly = true)
class ReportingService(
    private val jdbc: JdbcClient,
    private val settings: RestaurantSettingsApi,
    private val props: AmadyaProperties,
    private val json: JsonMapper,
) {
    private val sold = "status NOT IN ('CANCELLED', 'PENDING_PAYMENT')"

    fun dashboard(from: LocalDate, to: LocalDate): Dashboard {
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) throw UnprocessableException("reporting.invalid_range")
        val currency = settings.currency()
        val zone = props.timezone.id
        fun q(sql: String) = jdbc.sql(sql).param("from", from).param("to", to)

        val totals = q("SELECT count(*) n, coalesce(sum(total),0) revenue, coalesce(sum(vat_total),0) vat FROM customer_order WHERE business_date BETWEEN :from AND :to AND $sold")
            .query { rs, _ -> Triple(rs.getInt("n"), rs.getBigDecimal("revenue"), rs.getBigDecimal("vat")) }.single()
        val cancelled = q("SELECT count(*) FROM customer_order WHERE business_date BETWEEN :from AND :to AND status = 'CANCELLED' AND placed_at IS NOT NULL")
            .query(Int::class.java).single()
        val byChannel = q("SELECT channel, count(*) n, sum(total) revenue FROM customer_order WHERE business_date BETWEEN :from AND :to AND $sold GROUP BY channel ORDER BY channel")
            .query { rs, _ -> DashboardByChannelInner(OrderChannel.forValue(rs.getString("channel")), rs.getInt("n"), rs.getBigDecimal("revenue").toMoney(currency)) }.list()
        val byDay = q("SELECT business_date d, count(*) n, sum(total) revenue FROM customer_order WHERE business_date BETWEEN :from AND :to AND $sold GROUP BY business_date ORDER BY business_date")
            .query { rs, _ -> DashboardByDayInner(rs.getObject("d", LocalDate::class.java), rs.getInt("n"), rs.getBigDecimal("revenue").toMoney(currency)) }.list()
        val byHour = q(
            """SELECT extract(hour FROM coalesce(placed_at, created_at) AT TIME ZONE '$zone')::int h, count(*) n
               FROM customer_order WHERE business_date BETWEEN :from AND :to AND $sold GROUP BY h ORDER BY h""",
        ).query { rs, _ -> DashboardByHourInner(rs.getInt("h"), rs.getInt("n")) }.list()
        val locale = LocaleContextHolder.getLocale()
        val top = q(
            """SELECT l.product_id, (array_agg(l.product_name::text))[1] name, sum(l.quantity) qty, sum(l.total) revenue
               FROM order_line l JOIN customer_order o ON o.id = l.order_id
               WHERE o.business_date BETWEEN :from AND :to AND o.$sold
               GROUP BY l.product_id ORDER BY qty DESC, revenue DESC LIMIT 10""",
        ).query { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            val name = (json.readValue(rs.getString("name"), Map::class.java) as Map<String, String>).localized(locale)
            DashboardTopProductsInner(rs.getObject("product_id", UUID::class.java), name, rs.getInt("qty"), rs.getBigDecimal("revenue").toMoney(currency))
        }.list()
        val prep = q(
            """SELECT avg(extract(epoch FROM t.ready_at - t.queued_at))::int FROM kitchen_ticket t JOIN customer_order o ON o.id = t.order_id
               WHERE t.ready_at IS NOT NULL AND o.business_date BETWEEN :from AND :to""",
        ).query(Int::class.java).optional().orElse(null)
        val foodCost = q(
            """SELECT coalesce(-sum(m.total_cost), 0) FROM stock_movement m JOIN customer_order o ON o.id = m.source_id
               WHERE m.source_type IN ('ORDER', 'ORDER_RETURN') AND o.business_date BETWEEN :from AND :to""",
        ).query(BigDecimal::class.java).single()
        val stockValue = jdbc.sql("SELECT coalesce(sum(value), 0) FROM stock_balance").query(BigDecimal::class.java).single()
        val low = jdbc.sql(
            """SELECT count(*) FROM stock_balance b JOIN stock_item i ON i.id = b.stock_item_id
               WHERE b.quantity < i.min_stock OR b.quantity < 0""",
        ).query(Int::class.java).single()

        val (orders, revenue, vat) = totals
        return Dashboard(
            from = from,
            to = to,
            revenue = revenue.toMoney(currency),
            orders = orders,
            averageTicket = (if (orders == 0) BigDecimal.ZERO else revenue.divide(BigDecimal(orders), 2, RoundingMode.HALF_UP)).toMoney(currency),
            cancelled = cancelled,
            byChannel = byChannel,
            byDay = byDay,
            byHour = byHour,
            topProducts = top,
            stockValue = stockValue.toMoney(currency),
            lowStockCount = low,
            vat = vat.toMoney(currency),
            averagePrepSeconds = prep,
            foodCost = foodCost.toMoney(currency),
        )
    }
}

@RestController
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
class ReportingController(private val reporting: ReportingService) : ReportingApi {
    override fun getDashboard(from: LocalDate, to: LocalDate): ResponseEntity<Dashboard> = ResponseEntity.ok(reporting.dashboard(from, to))
}
