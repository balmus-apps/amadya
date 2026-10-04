package ro.amadya.procurement.internal

import org.w3c.dom.Element
import org.w3c.dom.Node
import ro.amadya.shared.UnprocessableException
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class ParsedInvoice(
    val number: String,
    val series: String?,
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val currency: String,
    val supplierName: String,
    val supplierCui: String?,
    val supplierRegCom: String?,
    val supplierAddress: String?,
    val lines: List<ParsedLine>,
)

data class ParsedLine(
    val supplierCode: String?,
    val description: String,
    val quantity: BigDecimal,
    val unitCode: String?,
    val unitPrice: BigDecimal,
    val vatPercent: BigDecimal,
    val lineNet: BigDecimal,
)

/**
 * Reads ANAF e-Factura invoices (UBL 2.1 Invoice, RO_CIUS profile). Elements are matched by local name,
 * so namespace prefixes used by different issuers do not matter. External entities are disabled (XXE-safe).
 */
object EFacturaParser {

    fun parse(xml: String): ParsedInvoice {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trim().removePrefix("﻿"))))
        } catch (e: Exception) {
            throw UnprocessableException("procurement.invalid_xml", e.message ?: "")
        }
        val root = doc.documentElement
        if (root.localName != "Invoice") throw UnprocessableException("procurement.invalid_xml", "root element ${root.localName}")

        val id = root.text("ID") ?: throw UnprocessableException("procurement.invalid_xml", "cbc:ID")
        val party = root.child("AccountingSupplierParty")?.child("Party") ?: throw UnprocessableException("procurement.invalid_xml", "AccountingSupplierParty")
        val legal = party.child("PartyLegalEntity")
        val supplierName = legal?.text("RegistrationName") ?: party.child("PartyName")?.text("Name") ?: throw UnprocessableException("procurement.invalid_xml", "RegistrationName")
        val cui = party.child("PartyTaxScheme")?.text("CompanyID") ?: legal?.text("CompanyID")
        val address = party.child("PostalAddress")?.let { a -> listOfNotNull(a.text("StreetName"), a.text("CityName"), a.text("CountrySubentity")).joinToString(", ").ifBlank { null } }

        val lines = root.children("InvoiceLine").map { line ->
            val qtyEl = line.child("InvoicedQuantity")
            val item = line.child("Item")
            ParsedLine(
                supplierCode = item?.child("SellersItemIdentification")?.text("ID"),
                description = item?.text("Name") ?: item?.text("Description") ?: "?",
                quantity = BigDecimal(qtyEl?.textContent?.trim() ?: "0"),
                unitCode = qtyEl?.getAttribute("unitCode")?.ifBlank { null },
                unitPrice = BigDecimal(line.child("Price")?.text("PriceAmount") ?: "0"),
                vatPercent = BigDecimal(item?.child("ClassifiedTaxCategory")?.text("Percent") ?: "0"),
                lineNet = BigDecimal(line.text("LineExtensionAmount") ?: "0"),
            )
        }
        if (lines.isEmpty()) throw UnprocessableException("procurement.invalid_xml", "no InvoiceLine")

        // "ABC 123" style numbers: keep the full id as number; the series is informational.
        val series = Regex("^([A-Za-z]+)[\\s-]*\\d").find(id)?.groupValues?.get(1)
        return ParsedInvoice(
            number = id,
            series = series,
            issueDate = LocalDate.parse(root.text("IssueDate") ?: throw UnprocessableException("procurement.invalid_xml", "IssueDate")),
            dueDate = root.text("DueDate")?.let(LocalDate::parse),
            currency = root.text("DocumentCurrencyCode") ?: "RON",
            supplierName = supplierName,
            supplierCui = cui?.let(::normalizeCui),
            supplierRegCom = legal?.text("CompanyID")?.takeIf { it.startsWith("J", ignoreCase = true) },
            supplierAddress = address,
            lines = lines,
        )
    }

    /** "RO 12345678" and "12345678" are the same company. */
    fun normalizeCui(raw: String): String = raw.uppercase().replace(Regex("[\\s.]"), "").removePrefix("RO")

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filter { it.nodeType == Node.ELEMENT_NODE && it.localName == name }.map { it as Element }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.text(name: String): String? = child(name)?.textContent?.trim()?.ifBlank { null }
}
