package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantStorageFoundation0252Test {
    @Test
    fun legacyScopeKeepsExistingKeysAndAliasesExactly() {
        val identity = RotaCertaTenantIdentity(
            tenantId = "provisional-device-tenant",
            storageNamespace = "",
            provisionalLocal = true,
        )
        val scope = TenantStoragePolicy.scopeFor(identity)

        assertTrue(scope.usesLegacyKeys)
        assertEquals("trips", scope.key("trips"))
        assertEquals("rota_certa_stage47_driver_token_aes", scope.keyAlias("rota_certa_stage47_driver_token_aes"))
    }

    @Test
    fun canonicalTenantsProduceStableDistinctNamespaces() {
        val first = TenantStoragePolicy.namespaceFor("tenant-alpha")
        val firstAgain = TenantStoragePolicy.namespaceFor("tenant-alpha")
        val second = TenantStoragePolicy.namespaceFor("tenant-beta")

        assertEquals(first, firstAgain)
        assertNotEquals(first, second)
        assertEquals(24, first.length)
    }

    @Test
    fun canonicalScopeSeparatesKeysSecretsAndGeneratedExternalAccountPrefixes() {
        val namespace = TenantStoragePolicy.namespaceFor("tenant-alpha")
        val scope = TenantStorageScope("tenant-alpha", namespace)

        assertFalse(scope.usesLegacyKeys)
        assertEquals("trips__tenant_$namespace", scope.key("trips"))
        assertEquals("rota_certa_stage47_driver_token_aes.$namespace", scope.keyAlias("rota_certa_stage47_driver_token_aes"))
        assertEquals("$namespace-", scope.scopedIdPrefix())
    }

    @Test
    fun tenantIdentityHasNoCountryCurrencyOrLocaleHardcodedDefaults() {
        val identity = RotaCertaTenantIdentity(tenantId = "tenant-alpha")

        assertEquals("", identity.currencyCode)
        assertEquals("", identity.localeTag)
        assertEquals("", identity.displayName)
        assertEquals("", identity.userId)
    }

    @Test
    fun financialParserAcceptsDifferentCurrencySymbolsAndDecimalConventions() {
        assertEquals(123_456L, FinancialRepository.parseCurrencyToCents("$ 1,234.56"))
        assertEquals(123_456L, FinancialRepository.parseCurrencyToCents("€ 1.234,56"))
        assertEquals(123_456L, FinancialRepository.parseCurrencyToCents("R$ 1.234,56"))
    }
}
