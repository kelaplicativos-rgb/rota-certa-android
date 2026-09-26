package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale
import kotlin.math.pow

internal data class PassengerMoneySpec(
    val currencyCode: String,
    val localeTag: String,
    val fractionDigits: Int,
)

internal object PassengerMoney {
    fun spec(context: Context): PassengerMoneySpec {
        val tenant = RotaCertaTenantRegistry(context.applicationContext).activeTenant()
        val locale = tenant.localeTag.trim().takeIf(String::isNotEmpty)
            ?.let(Locale::forLanguageTag)
            ?.takeUnless { it.language.isBlank() }
            ?: Locale.getDefault()
        val explicitCurrency = tenant.currencyCode.trim().uppercase(Locale.ROOT)
            .takeIf { code -> runCatching { Currency.getInstance(code) }.isSuccess }
        val localeCurrency = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        val code = explicitCurrency ?: localeCurrency.orEmpty()
        val fractionDigits = code.takeIf(String::isNotBlank)
            ?.let { runCatching { Currency.getInstance(it).defaultFractionDigits }.getOrNull() }
            ?.takeIf { it in 0..3 }
            ?: 2
        return PassengerMoneySpec(
            currencyCode = code,
            localeTag = locale.toLanguageTag(),
            fractionDigits = fractionDigits,
        )
    }

    fun parseMinorUnits(raw: String, spec: PassengerMoneySpec): Long? {
        val text = raw.trim().takeIf(String::isNotEmpty) ?: return null
        val locale = locale(spec.localeTag)
        val currency = spec.currencyCode.takeIf(String::isNotBlank)
            ?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        val candidates = buildList {
            add(NumberFormat.getNumberInstance(locale))
            currency?.let {
                add(NumberFormat.getCurrencyInstance(locale).apply { this.currency = it })
            }
        }
        for (format in candidates) {
            val position = ParsePosition(0)
            val parsed = format.parse(text, position)
            if (parsed != null && text.substring(position.index).trim().isEmpty()) {
                return numberToMinorUnits(parsed.toString(), spec.fractionDigits)
            }
        }

        // Safe fallback for plain values commonly typed on mobile. The right-most
        // separator is treated as decimal only when one or two digits follow it.
        val cleaned = text
            .replace(spec.currencyCode, "", ignoreCase = true)
            .replace(Regex("[^0-9,.-]"), "")
            .trim()
        if (cleaned.isBlank()) return null
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val separator = maxOf(lastComma, lastDot)
        val digitsAfter = if (separator >= 0) cleaned.length - separator - 1 else 0
        val normalized = when {
            separator >= 0 && digitsAfter in 1..2 -> {
                val integer = cleaned.substring(0, separator).replace(",", "").replace(".", "")
                val decimals = cleaned.substring(separator + 1).filter(Char::isDigit)
                "$integer.$decimals"
            }
            else -> cleaned.replace(",", "").replace(".", "")
        }
        return numberToMinorUnits(normalized, spec.fractionDigits)
    }

    fun formatMinorUnits(
        amountMinorUnits: Long,
        currencyCode: String,
        localeTag: String = "",
    ): String {
        val locale = locale(localeTag)
        val currency = currencyCode.trim().uppercase(Locale.ROOT).takeIf(String::isNotEmpty)
            ?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        val fractionDigits = currency?.defaultFractionDigits?.takeIf { it in 0..3 } ?: 2
        val divisor = BigDecimal.TEN.pow(fractionDigits)
        val value = BigDecimal.valueOf(amountMinorUnits).divide(divisor)
        return if (currency != null) {
            NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }.format(value)
        } else {
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }.format(value)
        }
    }

    private fun numberToMinorUnits(raw: String, fractionDigits: Int): Long? = runCatching {
        val decimal = BigDecimal(raw)
        require(decimal > BigDecimal.ZERO)
        val multiplier = BigDecimal.TEN.pow(fractionDigits)
        decimal.multiply(multiplier)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
            .takeIf { it in 1L..1_000_000_000_000L }
    }.getOrNull()

    private fun locale(tag: String): Locale = tag.trim().takeIf(String::isNotEmpty)
        ?.let(Locale::forLanguageTag)
        ?.takeUnless { it.language.isBlank() }
        ?: Locale.getDefault()
}
