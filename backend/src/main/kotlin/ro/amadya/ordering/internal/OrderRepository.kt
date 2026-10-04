package ro.amadya.ordering.internal

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface OrderRepository : JpaRepository<OrderEntity, UUID> {

    @Query(
        """SELECT o FROM OrderEntity o
           WHERE o.status IN :statuses AND (:channel IS NULL OR o.channel = :channel)
           ORDER BY o.createdAt DESC""",
    )
    fun search(statuses: Collection<OrderStatus>, channel: OrderChannel?, limit: Limit): List<OrderEntity>

    fun findAllByBusinessDateAndChannelInAndStatusInOrderByPlacedAtAsc(
        businessDate: LocalDate,
        channels: Collection<OrderChannel>,
        statuses: Collection<OrderStatus>,
    ): List<OrderEntity>

    fun findAllByStatusAndCreatedAtBefore(status: OrderStatus, createdBefore: Instant): List<OrderEntity>
}

/** Daily order numbers (B-001, B-002, ...) using an atomic upsert per business day. */
@Component
class OrderNumberGenerator(private val jdbc: JdbcClient) {
    fun next(businessDate: LocalDate, prefix: String): String {
        val value = jdbc.sql(
            """INSERT INTO order_number_counter (business_date, last_value) VALUES (:d, 1)
               ON CONFLICT (business_date) DO UPDATE SET last_value = order_number_counter.last_value + 1
               RETURNING last_value""",
        ).param("d", businessDate).query(Int::class.java).single()
        return "%s-%03d".format(prefix, value)
    }
}
