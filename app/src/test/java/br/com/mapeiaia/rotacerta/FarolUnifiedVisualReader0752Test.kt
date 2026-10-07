package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FarolUnifiedVisualReader0752Test {
    @Test
    fun visual_order_selects_last_positive_address_as_destination() {
        val pass = OcrStructuredText0188(
            text = "Rua das Flores, 100\nAvenida Paulista, 1578",
            blocks = listOf(
                OcrTextBlock0188("b", "Avenida Paulista, 1578", 20, 700, 900, 780),
                OcrTextBlock0188("a", "Rua das Flores, 100", 20, 300, 900, 380),
            ),
        )
        val addresses = ScreenAddressVisualAuthority0752.orderedAddresses("", listOf(pass))
        assertEquals(listOf("Rua das Flores, 100", "Avenida Paulista, 1578"), addresses)
        assertEquals("Avenida Paulista, 1578", ScreenAddressVisualAuthority0752.destination(addresses))
    }

    @Test
    fun recovery_pass_duplicates_do_not_change_last_address_authority() {
        val primary = OcrStructuredText0188(
            text = "Rua das Flores, 100",
            blocks = listOf(OcrTextBlock0188("p0", "Rua das Flores, 100", 10, 100, 800, 180)),
        )
        val recovery = OcrStructuredText0188(
            text = "Rua das Flores, 100\nRua Augusta, 900",
            blocks = listOf(
                OcrTextBlock0188("r0", "Rua das Flores, 100", 12, 102, 802, 182),
                OcrTextBlock0188("r1", "Rua Augusta, 900", 10, 600, 800, 680),
            ),
        )
        val addresses = ScreenAddressVisualAuthority0752.orderedAddresses("", listOf(primary, recovery))
        assertEquals(2, addresses.size)
        assertEquals("Rua Augusta, 900", addresses.last())
    }

    @Test
    fun any_external_app_with_two_addresses_is_authorized_but_own_or_system_is_not() {
        val pair = listOf("Rua das Flores, 100", "Rua Augusta, 900")
        assertTrue(FarolUniversalAddressPairAuthority0752.authorize(pair, "com.example.anyapp", "br.com.mapeiaia.rotacerta"))
        assertFalse(FarolUniversalAddressPairAuthority0752.authorize(pair.take(1), "com.example.anyapp", "br.com.mapeiaia.rotacerta"))
        assertFalse(FarolUniversalAddressPairAuthority0752.authorize(pair, "br.com.mapeiaia.rotacerta", "br.com.mapeiaia.rotacerta"))
        assertFalse(FarolUniversalAddressPairAuthority0752.authorize(pair, "com.android.systemui", "br.com.mapeiaia.rotacerta"))
    }

    @Test
    fun pair_evaluation_routes_to_last_address() {
        val evaluation = FarolUniversalAddressPairAuthority0752.evaluation(
            windowId = 42,
            addresses = listOf("Rua das Flores, 100", "Rua Augusta, 900", "Avenida Paulista, 1578"),
        )
        assertNotNull(evaluation)
        assertEquals("Rua das Flores, 100", evaluation.pickup)
        assertEquals("Avenida Paulista, 1578", evaluation.destination)
        assertEquals(3, evaluation.addresses.size)
    }
}
