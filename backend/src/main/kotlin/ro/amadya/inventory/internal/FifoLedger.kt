package ro.amadya.inventory.internal

import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ro.amadya.shared.ConflictException
import ro.amadya.shared.Ids
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class Source(val type: String, val id: UUID?, val ref: String?, val userId: UUID? = null)

data class Allocation(val lotId: UUID, val receivedAt: Instant, val quantity: BigDecimal, val unitCost: BigDecimal)

data class Issue(val movementId: UUID, val cost: BigDecimal, val allocations: List<Allocation>, val deficit: BigDecimal, val deficitUnitCost: BigDecimal)

/**
 * FIFO stock ledger (docs/domain/warehouses.md). All quantities are in the item's base unit.
 *
 * - receive: one lot per receipt; open deficits of the same warehouse/item are settled from it first.
 * - issue: oldest lots first (row-locked); anything not covered becomes a deficit at the last known cost.
 * - balances are recomputed from lots and deficits after every change, so they can never drift from the ledger.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class FifoLedger(private val jdbc: JdbcClient, private val em: EntityManager, private val clock: Clock) {

    fun receive(warehouseId: UUID, itemId: UUID, quantity: BigDecimal, unitCost: BigDecimal, receivedAt: Instant, type: String, source: Source): UUID {
        require(quantity.signum() > 0) { "receipt quantity must be positive" }
        em.flush()
        val movementId = insertMovement(warehouseId, itemId, type, quantity, quantity.multiply(unitCost), source)
        val lotId = Ids.newId()
        jdbc.sql(
            """INSERT INTO stock_lot (id, warehouse_id, stock_item_id, received_at, qty_initial, qty_remaining, unit_cost, movement_id)
               VALUES (:id, :w, :i, :r, :q, :q, :c, :m)""",
        ).param("id", lotId).param("w", warehouseId).param("i", itemId).param("r", Timestamp.from(receivedAt))
            .param("q", quantity).param("c", unitCost.setScale(6, RoundingMode.HALF_UP)).param("m", movementId).update()

        settleDeficits(warehouseId, itemId, lotId, quantity, unitCost)
        recomputeBalance(warehouseId, itemId)
        return movementId
    }

    fun issue(warehouseId: UUID, itemId: UUID, quantity: BigDecimal, type: String, source: Source): Issue {
        require(quantity.signum() > 0) { "issue quantity must be positive" }
        em.flush()
        val movementId = insertMovement(warehouseId, itemId, type, quantity.negate(), BigDecimal.ZERO, source)
        val lots = jdbc.sql(
            """SELECT id, received_at, qty_remaining, unit_cost FROM stock_lot
               WHERE warehouse_id = :w AND stock_item_id = :i AND qty_remaining > 0
               ORDER BY received_at, seq FOR UPDATE""",
        ).param("w", warehouseId).param("i", itemId).query { rs, _ ->
            Allocation(rs.getObject("id", UUID::class.java), rs.getTimestamp("received_at").toInstant(), rs.getBigDecimal("qty_remaining"), rs.getBigDecimal("unit_cost"))
        }.list()

        var remaining = quantity
        val allocations = mutableListOf<Allocation>()
        for (lot in lots) {
            if (remaining.signum() == 0) break
            val take = lot.quantity.min(remaining)
            jdbc.sql("UPDATE stock_lot SET qty_remaining = qty_remaining - :q WHERE id = :id").param("q", take).param("id", lot.lotId).update()
            insertAllocation(movementId, lot.lotId, take, lot.unitCost)
            allocations += lot.copy(quantity = take)
            remaining -= take
        }

        var deficitCost = BigDecimal.ZERO
        if (remaining.signum() > 0) {
            deficitCost = lastUnitCost(warehouseId, itemId)
            jdbc.sql(
                """INSERT INTO stock_deficit (id, warehouse_id, stock_item_id, movement_id, qty_open, estimated_unit_cost)
                   VALUES (:id, :w, :i, :m, :q, :c)""",
            ).param("id", Ids.newId()).param("w", warehouseId).param("i", itemId).param("m", movementId).param("q", remaining).param("c", deficitCost).update()
        }
        val cost = allocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.quantity * a.unitCost } + remaining * deficitCost
        setMovementCost(movementId, cost.negate())
        recomputeBalance(warehouseId, itemId)
        return Issue(movementId, cost, allocations, remaining, deficitCost)
    }

    /** Moves goods keeping each lot's receipt date and cost, so FIFO order and value travel with them. */
    fun transfer(from: UUID, to: UUID, itemId: UUID, quantity: BigDecimal, source: Source): BigDecimal {
        val out = issue(from, itemId, quantity, "TRANSFER_OUT", source)
        out.allocations.forEach { receive(to, itemId, it.quantity, it.unitCost, it.receivedAt, "TRANSFER_IN", source) }
        if (out.deficit.signum() > 0) receive(to, itemId, out.deficit, out.deficitUnitCost, clock.instant(), "TRANSFER_IN", source)
        return out.cost
    }

    /** Puts back everything a source consumed (order cancelled): lots are restored and open deficits dropped. */
    fun returnIssues(sourceType: String, sourceId: UUID, returnType: String, ref: String?) {
        em.flush()
        val movements = jdbc.sql(
            "SELECT id, warehouse_id, stock_item_id, quantity, total_cost FROM stock_movement WHERE source_type = :t AND source_id = :s AND quantity < 0",
        ).param("t", sourceType).param("s", sourceId).query { rs, _ ->
            Triple(rs.getObject("id", UUID::class.java), rs.getObject("warehouse_id", UUID::class.java) to rs.getObject("stock_item_id", UUID::class.java), rs.getBigDecimal("quantity").negate() to rs.getBigDecimal("total_cost").negate())
        }.list()
        val touched = mutableSetOf<Pair<UUID, UUID>>()
        for ((movementId, key, amounts) in movements) {
            jdbc.sql(
                """UPDATE stock_lot l SET qty_remaining = l.qty_remaining + a.quantity
                   FROM stock_lot_allocation a WHERE a.lot_id = l.id AND a.movement_id = :m""",
            ).param("m", movementId).update()
            jdbc.sql("UPDATE stock_deficit SET qty_open = 0 WHERE movement_id = :m").param("m", movementId).update()
            insertMovement(key.first, key.second, returnType, amounts.first, amounts.second, Source("${sourceType}_RETURN", sourceId, ref))
            touched += key
        }
        touched.forEach { recomputeBalance(it.first, it.second) }
    }

    /** Reverses receipts of a source document (NIR); only allowed while none of their lots was consumed. */
    fun reverseReceipts(sourceType: String, sourceId: UUID, ref: String) {
        em.flush()
        data class Lot(val id: UUID, val warehouseId: UUID, val itemId: UUID, val initial: BigDecimal, val remaining: BigDecimal, val unitCost: BigDecimal)
        val lots = jdbc.sql(
            """SELECT l.id, l.warehouse_id, l.stock_item_id, l.qty_initial, l.qty_remaining, l.unit_cost
               FROM stock_lot l JOIN stock_movement m ON m.id = l.movement_id
               WHERE m.source_type = :t AND m.source_id = :s FOR UPDATE OF l""",
        ).param("t", sourceType).param("s", sourceId).query { rs, _ ->
            Lot(
                rs.getObject("id", UUID::class.java), rs.getObject("warehouse_id", UUID::class.java), rs.getObject("stock_item_id", UUID::class.java),
                rs.getBigDecimal("qty_initial"), rs.getBigDecimal("qty_remaining"), rs.getBigDecimal("unit_cost"),
            )
        }.list()
        if (lots.any { it.remaining.compareTo(it.initial) != 0 }) throw ConflictException("nir.lots_consumed")
        for (lot in lots) {
            jdbc.sql("UPDATE stock_lot SET qty_remaining = 0 WHERE id = :id").param("id", lot.id).update()
            insertMovement(lot.warehouseId, lot.itemId, "REVERSAL", lot.initial.negate(), (lot.initial * lot.unitCost).negate(), Source(sourceType, sourceId, ref))
        }
        lots.map { it.warehouseId to it.itemId }.toSet().forEach { recomputeBalance(it.first, it.second) }
    }

    fun quantityOnHand(warehouseId: UUID, itemId: UUID): BigDecimal {
        em.flush()
        return jdbc.sql("SELECT quantity FROM stock_balance WHERE warehouse_id = :w AND stock_item_id = :i")
            .param("w", warehouseId).param("i", itemId).query(BigDecimal::class.java).optional().orElse(BigDecimal.ZERO)
    }

    /** Cost of the next unit to be issued: oldest open lot, else the last known receipt cost. */
    fun currentUnitCost(warehouseId: UUID?, itemId: UUID): BigDecimal =
        jdbc.sql(
            """SELECT unit_cost FROM stock_lot WHERE stock_item_id = :i AND (CAST(:w AS uuid) IS NULL OR warehouse_id = :w)
               ORDER BY (qty_remaining > 0) DESC, CASE WHEN qty_remaining > 0 THEN received_at END ASC, received_at DESC, seq DESC LIMIT 1""",
        ).param("i", itemId).param("w", warehouseId).query(BigDecimal::class.java).optional().orElse(BigDecimal.ZERO)

    fun lastUnitCost(warehouseId: UUID, itemId: UUID): BigDecimal =
        jdbc.sql("SELECT unit_cost FROM stock_lot WHERE stock_item_id = :i ORDER BY (warehouse_id = :w) DESC, received_at DESC, seq DESC LIMIT 1")
            .param("i", itemId).param("w", warehouseId).query(BigDecimal::class.java).optional().orElse(BigDecimal.ZERO)

    // ---------------------------------------------------------------- internals
    private fun settleDeficits(warehouseId: UUID, itemId: UUID, lotId: UUID, quantity: BigDecimal, unitCost: BigDecimal) {
        data class Deficit(val id: UUID, val movementId: UUID, val open: BigDecimal, val estimate: BigDecimal)
        val deficits = jdbc.sql(
            """SELECT id, movement_id, qty_open, estimated_unit_cost FROM stock_deficit
               WHERE warehouse_id = :w AND stock_item_id = :i AND qty_open > 0 ORDER BY created_at FOR UPDATE""",
        ).param("w", warehouseId).param("i", itemId).query { rs, _ ->
            Deficit(rs.getObject("id", UUID::class.java), rs.getObject("movement_id", UUID::class.java), rs.getBigDecimal("qty_open"), rs.getBigDecimal("estimated_unit_cost"))
        }.list()
        var available = quantity
        for (d in deficits) {
            if (available.signum() == 0) break
            val take = d.open.min(available)
            jdbc.sql("UPDATE stock_lot SET qty_remaining = qty_remaining - :q WHERE id = :id").param("q", take).param("id", lotId).update()
            jdbc.sql("UPDATE stock_deficit SET qty_open = qty_open - :q WHERE id = :id").param("q", take).param("id", d.id).update()
            insertAllocation(d.movementId, lotId, take, unitCost)
            // The issue was valued at an estimate; correct its cost to the real FIFO cost.
            jdbc.sql("UPDATE stock_movement SET total_cost = total_cost - :delta WHERE id = :m")
                .param("delta", take * (unitCost - d.estimate)).param("m", d.movementId).update()
            available -= take
        }
    }

    private fun insertMovement(warehouseId: UUID, itemId: UUID, type: String, quantity: BigDecimal, cost: BigDecimal, source: Source): UUID {
        val id = Ids.newId()
        jdbc.sql(
            """INSERT INTO stock_movement (id, warehouse_id, stock_item_id, type, quantity, total_cost, source_type, source_id, source_ref, user_id, occurred_at)
               VALUES (:id, :w, :i, :t, :q, :c, :st, :sid, :sref, :u, :at)""",
        ).param("id", id).param("w", warehouseId).param("i", itemId).param("t", type).param("q", quantity)
            .param("c", cost.setScale(4, RoundingMode.HALF_UP)).param("st", source.type).param("sid", source.id).param("sref", source.ref)
            .param("u", source.userId).param("at", Timestamp.from(clock.instant())).update()
        return id
    }

    private fun insertAllocation(movementId: UUID, lotId: UUID, quantity: BigDecimal, unitCost: BigDecimal) {
        jdbc.sql("INSERT INTO stock_lot_allocation (id, movement_id, lot_id, quantity, unit_cost) VALUES (:id, :m, :l, :q, :c)")
            .param("id", Ids.newId()).param("m", movementId).param("l", lotId).param("q", quantity).param("c", unitCost).update()
    }

    private fun setMovementCost(movementId: UUID, cost: BigDecimal) {
        jdbc.sql("UPDATE stock_movement SET total_cost = :c WHERE id = :id").param("c", cost.setScale(4, RoundingMode.HALF_UP)).param("id", movementId).update()
    }

    private fun recomputeBalance(warehouseId: UUID, itemId: UUID) {
        jdbc.sql(
            """INSERT INTO stock_balance (warehouse_id, stock_item_id, quantity, value, updated_at)
               SELECT :w, :i,
                      COALESCE((SELECT sum(qty_remaining) FROM stock_lot WHERE warehouse_id = :w AND stock_item_id = :i), 0)
                    - COALESCE((SELECT sum(qty_open) FROM stock_deficit WHERE warehouse_id = :w AND stock_item_id = :i), 0),
                      COALESCE((SELECT sum(qty_remaining * unit_cost) FROM stock_lot WHERE warehouse_id = :w AND stock_item_id = :i), 0)
                    - COALESCE((SELECT sum(qty_open * estimated_unit_cost) FROM stock_deficit WHERE warehouse_id = :w AND stock_item_id = :i), 0),
                      now()
               ON CONFLICT (warehouse_id, stock_item_id) DO UPDATE
               SET quantity = EXCLUDED.quantity, value = EXCLUDED.value, updated_at = EXCLUDED.updated_at""",
        ).param("w", warehouseId).param("i", itemId).update()
    }
}
