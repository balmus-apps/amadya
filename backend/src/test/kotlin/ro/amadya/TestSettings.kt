package ro.amadya

import org.springframework.jdbc.core.simple.JdbcClient

/** Online payment is off by default (V17); tests that exercise it turn it on, and the tests that need it off say so. */
fun JdbcClient.onlinePayments(on: Boolean) {
    sql("UPDATE restaurant_settings SET features = features || jsonb_build_object('onlinePayments', ?::boolean)").param(on).update()
}
