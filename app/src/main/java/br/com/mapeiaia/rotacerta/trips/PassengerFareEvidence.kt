package br.com.mapeiaia.rotacerta.trips

import java.util.Currency
import java.util.Locale

internal fun normalizePassengerFareCurrency(raw: String?): String? {
    val code = raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 3 && it.all(Char::isLetter) } ?: return null
    return code.takeIf { runCatching { Currency.getInstance(it) }.isSuccess }
}

internal fun resolvePassengerFareCurrency(pageCurrency: String?, tenantCurrency: String?): String? =
    normalizePassengerFareCurrency(pageCurrency) ?: normalizePassengerFareCurrency(tenantCurrency)
