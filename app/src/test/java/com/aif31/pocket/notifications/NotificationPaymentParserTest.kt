package com.aif31.pocket.notifications

import com.aif31.pocket.domain.SupportedCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPaymentParserTest {
    @Test fun english_and_spanish_payments_are_normalized() {
        assertEquals(
            ParsedPayment(1_234, SupportedCurrency.SAR, "Corner Shop"),
            NotificationPaymentParser.parse("Payment approved", "Paid SAR 12.34 at Corner Shop"),
        )
        assertEquals(
            ParsedPayment(2_500, SupportedCurrency.MXN, "Tienda"),
            NotificationPaymentParser.parse("Compra aprobada", "Pagaste MX$ 25,00 en Tienda"),
        )
    }

    @Test fun supported_currency_symbols_and_number_formats_are_normalized() {
        val cases = listOf(
            "Paid USD 1,234.56" to ParsedPayment(123_456, SupportedCurrency.USD, null),
            "Paid US$ 10" to ParsedPayment(1_000, SupportedCurrency.USD, null),
            "Pagaste MXN 1.234,56" to ParsedPayment(123_456, SupportedCurrency.MXN, null),
            "Paid SAR 9.5" to ParsedPayment(950, SupportedCurrency.SAR, null),
            "Paid ر. س. 12.00" to ParsedPayment(1_200, SupportedCurrency.SAR, null),
        )

        cases.forEach { (content, expected) ->
            assertEquals(content, expected, NotificationPaymentParser.parse("Payment", content))
        }
        assertNull(NotificationPaymentParser.parse("Payment", "Paid USD 999999999999999999999999.00"))
    }

    @Test fun ambiguous_malformed_and_unrelated_notifications_are_rejected() {
        assertNull(NotificationPaymentParser.parse("Payment", "Paid SAR 12.00; fee SAR 1.00"))
        assertNull(NotificationPaymentParser.parse("Payment", "Paid SAR 1,23,4.56"))
        assertNull(NotificationPaymentParser.parse("Card notice", "Your card ending 1234 is active"))
        assertNull(NotificationPaymentParser.parse("Pago", "Pagaste $25.00 en Tienda"))
        assertNull(NotificationPaymentParser.parse("Refund", "Refunded USD 10.00"))
        assertNull(NotificationPaymentParser.parse("Reversión", "Reversión de compra MXN 40.00"))
        assertNull(NotificationPaymentParser.parse("Payment declined", "Payment declined USD 10.00"))
        assertNull(NotificationPaymentParser.parse("Pago rechazado", "Pago rechazado MXN 20.00"))
        assertNull(NotificationPaymentParser.parse("Verification", "OTP code for payment USD 10.00"))
        assertNull(NotificationPaymentParser.parse("Payment due", "Payment due USD 20.00"))
        assertNull(NotificationPaymentParser.parse("Payment", "Paid USD -10.00"))
    }

    @Test fun unexpected_notification_text_failure_is_rejected_without_a_crash() {
        val hostile = object : CharSequence {
            override val length: Int = 1
            override fun get(index: Int): Char = 'x'
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this
            override fun toString(): String = error("synthetic notification content must not escape")
        }

        assertNull(NotificationPaymentParser.parse(hostile, null))
    }

    @Test fun uncertain_merchant_metadata_is_not_persisted() {
        assertEquals(
            ParsedPayment(1_200, SupportedCurrency.USD, null),
            NotificationPaymentParser.parse("Payment", "Paid USD 12.00 at Shop card 1234"),
        )
    }
}
