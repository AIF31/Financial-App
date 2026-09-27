package com.aif31.pocket.notifications

import com.aif31.pocket.domain.SupportedCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic messages shaped like real bank SMS; card digits and merchants are placeholders. */
class BankNotificationFormatsTest {
    @Test fun multi_line_online_purchase_reads_amount_and_labelled_merchant() {
        val text = "Online Purchase\nBy: ***1111;mada\nFrom: ***002\nAmount: SAR 243.18\n" +
            "At: AMAZON SA××Madina×\nDate: 2026-09-22 08:55:22"

        assertEquals(
            ParsedPayment(24_318, SupportedCurrency.SAR, "AMAZON SA Madina"),
            NotificationPaymentParser.parse("SAB", text),
        )
    }

    @Test fun point_of_sale_purchase_stops_the_merchant_before_the_amount() {
        val text = "PoS Purchase\nSAB mada(Google Pay) Card (1111) from (***002) was used at " +
            "TAMIMI GLOBAL C×KIN for SAR 23.05 on 2026-09-22 11:48:22."

        assertEquals(
            ParsedPayment(2_305, SupportedCurrency.SAR, "TAMIMI GLOBAL C KIN"),
            NotificationPaymentParser.parse("SAB", text),
        )
    }

    @Test fun short_store_codes_are_kept_in_merchant_names() {
        val text = "PoS Purchase\nCard (1111) from (***002) was used at E206 Tamimi Express for SAR 33.64 on 2026-09-22 22:13:23."

        assertEquals("E206 Tamimi Express", NotificationPaymentParser.parse("SAB", text)?.merchant)
    }

    @Test fun foreign_currency_purchases_keep_their_original_currency() {
        val text = "Online Purchase\nBy: ***1111;mada\nFrom: ***002\nAmount: MXN 731.18\n" +
            "At: MERPAGO*MERCADOLENDING\nDate: 2026-09-23 00:14:10"

        assertEquals(
            ParsedPayment(73_118, SupportedCurrency.MXN, "MERPAGO*MERCADOLENDING"),
            NotificationPaymentParser.parse("SAB", text),
        )
    }

    @Test fun credits_and_one_time_passwords_are_never_payments() {
        assertNull(NotificationPaymentParser.parse("SAB", "Fund Transfer Credited\nTo: **002\nAmount: SAR 5,250.00\nOn: 2026-09-22 22:17:10"))
        assertNull(NotificationPaymentParser.parse("SAB", "OTP Code: 1778\nReason: SAB Mobile Login\nSharing OTP Exposes You to Fraud"))
        assertNull(NotificationPaymentParser.parse("Banco", "Abono recibido por MXN 500.00"))
        assertNull(NotificationPaymentParser.parse("SAB", "Cashback credited\nAmount: SAR 5.00 for your purchase at Corner Cafe"))
    }

    @Test fun bare_dollar_amounts_need_an_explicit_dollar_currency() {
        val text = "Compra aprobada por $250.00 en OXXO el 22/09"

        assertNull(NotificationPaymentParser.parse("Banco", text))
        assertNull(NotificationPaymentParser.parse("Banco", text, SupportedCurrency.SAR))
        assertEquals(
            ParsedPayment(25_000, SupportedCurrency.MXN, "OXXO"),
            NotificationPaymentParser.parse("Banco", text, SupportedCurrency.MXN),
        )
    }

    @Test fun online_purchase_wording_is_not_mistaken_for_the_merchant() {
        assertEquals(
            "OXXO",
            NotificationPaymentParser.parse("Banco", "Compra en línea por MXN 100.00 en OXXO", SupportedCurrency.MXN)?.merchant,
        )
        assertEquals(
            "AMAZON",
            NotificationPaymentParser.parse("Bank", "Online purchase of USD 20.00 at AMAZON")?.merchant,
        )
    }

    @Test fun merchant_keys_ignore_case_and_punctuation() {
        assertEquals(merchantKey("TAMIMI GLOBAL C KIN"), merchantKey("Tamimi Global C×Kin"))
        assertNull(merchantKey("  ·  "))
    }
}
