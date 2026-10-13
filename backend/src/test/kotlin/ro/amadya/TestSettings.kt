package ro.amadya

import org.springframework.jdbc.core.simple.JdbcClient

/** Online payment is off by default (V17); tests that exercise it turn it on, and the tests that need it off say so. */
fun JdbcClient.onlinePayments(on: Boolean) {
    sql("UPDATE restaurant_settings SET features = features || jsonb_build_object('onlinePayments', ?::boolean)").param(on).update()
}

/** Minutes a customer's to-go order paid at pickup waits for the cashier; 0 = no confirmation. */
fun JdbcClient.takeawayConfirmMinutes(minutes: Int) {
    sql("UPDATE restaurant_settings SET takeaway_confirm_minutes = ?").param(minutes).update()
}
