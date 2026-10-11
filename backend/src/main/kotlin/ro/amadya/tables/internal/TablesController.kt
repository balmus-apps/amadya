package ro.amadya.tables.internal

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import ro.amadya.contract.api.TablesApi
import ro.amadya.contract.model.AddRoundRequest
import ro.amadya.contract.model.DiningTableAdmin
import ro.amadya.contract.model.DiningTableRequest
import ro.amadya.contract.model.FloorArea
import ro.amadya.contract.model.FloorAreaAdmin
import ro.amadya.contract.model.FloorAreaRequest
import ro.amadya.contract.model.MergeTableRequest
import ro.amadya.contract.model.OpenTableSessionRequest
import ro.amadya.contract.model.TablePaymentRequest
import ro.amadya.contract.model.TableSession
import ro.amadya.contract.model.TransferTableRequest
import java.util.UUID

private const val FLOOR_STAFF = "hasAnyRole('ADMIN','MANAGER','WAITER','CASHIER')"
private const val MANAGERS = "hasAnyRole('ADMIN','MANAGER')"

/** Transactional so the views can read lazy order lines (open-in-view is off). */
@RestController
@Transactional
class TablesController(private val service: TablesService) : TablesApi {
    private fun locale() = LocaleContextHolder.getLocale()

    @PreAuthorize(MANAGERS)
    override fun listFloorAreas(): ResponseEntity<List<FloorAreaAdmin>> = ResponseEntity.ok(service.listAreas())

    @PreAuthorize(MANAGERS)
    override fun createFloorArea(floorAreaRequest: FloorAreaRequest): ResponseEntity<FloorAreaAdmin> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createArea(floorAreaRequest))

    @PreAuthorize(MANAGERS)
    override fun updateFloorArea(id: UUID, floorAreaRequest: FloorAreaRequest): ResponseEntity<FloorAreaAdmin> =
        ResponseEntity.ok(service.updateArea(id, floorAreaRequest))

    @PreAuthorize(MANAGERS)
    override fun listDiningTables(): ResponseEntity<List<DiningTableAdmin>> = ResponseEntity.ok(service.listTables())

    @PreAuthorize(MANAGERS)
    override fun createDiningTable(diningTableRequest: DiningTableRequest): ResponseEntity<DiningTableAdmin> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createTable(diningTableRequest))

    @PreAuthorize(MANAGERS)
    override fun updateDiningTable(id: UUID, diningTableRequest: DiningTableRequest): ResponseEntity<DiningTableAdmin> =
        ResponseEntity.ok(service.updateTable(id, diningTableRequest))

    @PreAuthorize(FLOOR_STAFF)
    override fun getFloor(acceptLanguage: String?): ResponseEntity<List<FloorArea>> = ResponseEntity.ok(service.floor(locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun openTableSession(id: UUID, openTableSessionRequest: OpenTableSessionRequest): ResponseEntity<TableSession> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.open(id, openTableSessionRequest.guests, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun getTableSession(id: UUID, acceptLanguage: String?): ResponseEntity<TableSession> = ResponseEntity.ok(service.get(id, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun addTableRound(id: UUID, addRoundRequest: AddRoundRequest, acceptLanguage: String?): ResponseEntity<TableSession> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.addRound(id, addRoundRequest, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun recordTablePayment(id: UUID, tablePaymentRequest: TablePaymentRequest, acceptLanguage: String?): ResponseEntity<TableSession> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.pay(id, tablePaymentRequest, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun voidTablePayment(id: UUID, paymentId: UUID, acceptLanguage: String?): ResponseEntity<TableSession> =
        ResponseEntity.ok(service.void(id, paymentId, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun closeTableSession(id: UUID, acceptLanguage: String?): ResponseEntity<TableSession> = ResponseEntity.ok(service.close(id, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun transferTableSession(id: UUID, transferTableRequest: TransferTableRequest, acceptLanguage: String?): ResponseEntity<TableSession> =
        ResponseEntity.ok(service.transfer(id, transferTableRequest.tableId, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun mergeTableSession(id: UUID, mergeTableRequest: MergeTableRequest, acceptLanguage: String?): ResponseEntity<TableSession> =
        ResponseEntity.ok(service.merge(id, mergeTableRequest.targetSessionId, locale()))

    @PreAuthorize(FLOOR_STAFF)
    override fun printPreBill(id: UUID): ResponseEntity<Unit> {
        service.preBill(id)
        return ResponseEntity.accepted().build()
    }
}
