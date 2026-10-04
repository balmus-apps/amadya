package ro.amadya.shared

import java.math.BigDecimal
import java.math.RoundingMode
import ro.amadya.contract.model.Money as MoneyDto

object Money {
    fun of(amount: String): BigDecimal = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)

    /** VAT included in a gross amount: gross * rate / (100 + rate). */
    fun vatOfGross(gross: BigDecimal, ratePercent: BigDecimal): BigDecimal =
        if (ratePercent.signum() == 0) BigDecimal.ZERO.setScale(2)
        else gross.multiply(ratePercent).divide(BigDecimal(100).add(ratePercent), 2, RoundingMode.HALF_UP)

    /** Amount in minor units (bani / cents) for payment providers. */
    fun toMinorUnits(amount: BigDecimal): Long = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}

fun BigDecimal.toMoney(currency: String) = MoneyDto(amount = setScale(2, RoundingMode.HALF_UP).toPlainString(), currency = currency)
