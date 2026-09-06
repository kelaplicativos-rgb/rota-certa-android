package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PublicDriverProfileTest {
    private val uuidA = "7371f028-9c55-4903-8444-308015823efd"
    private val uuidB = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    @Test
    fun exactUuidMergePreservesPreviouslyConfirmedMissingFields() {
        val previous = BlaBlaPublicProfileSnapshot(
            accountId = "a",
            profileUuid = uuidA,
            profileName = "Nome A",
            photoUrl = "https://example.test/a.jpg",
            about = "Apresentação anterior",
            rating = "4.9",
            reviewCount = 120,
            lastSyncedAtMillis = 10L,
        )
        val result = BlaBlaPublicProfileModule.mergeConfirmed(
            accountId = "a",
            expectedUuid = uuidA,
            previous = previous,
            capture = BlaBlaPublicProfileCapture(
                observedProfileUuid = uuidA,
                profileName = "Nome A atualizado",
                photoUrl = "",
                about = "",
                rating = "5.0",
                reviewCount = null,
            ),
            nowMillis = 20L,
        )
        val accepted = assertIs<BlaBlaPublicProfileMergeResult.Accepted>(result).snapshot
        assertEquals("Nome A atualizado", accepted.profileName)
        assertEquals("https://example.test/a.jpg", accepted.photoUrl)
        assertEquals("Apresentação anterior", accepted.about)
        assertEquals("5.0", accepted.rating)
        assertEquals(120, accepted.reviewCount)
        assertEquals(20L, accepted.lastSyncedAtMillis)
    }

    @Test
    fun differentUuidIsRejectedWithoutCrossContamination() {
        val result = BlaBlaPublicProfileModule.mergeConfirmed(
            accountId = "a",
            expectedUuid = uuidA,
            previous = null,
            capture = BlaBlaPublicProfileCapture(
                observedProfileUuid = uuidB,
                profileName = "Outro perfil",
                photoUrl = "https://example.test/b.jpg",
            ),
        )
        assertIs<BlaBlaPublicProfileMergeResult.RejectedIdentity>(result)
    }

    @Test
    fun manualModeNeverUsesAutomaticProfileFields() {
        val settings = TripOnlineSettings(
            driverDisplayName = "Meu nome",
            driverWhatsapp = "11999999999",
            driverPhotoUrl = "https://example.test/manual.jpg",
            driverPublicAbout = "Manual",
            paymentInstructions = "Pix",
            publicProfileMode = PublicDriverProfileMode.MANUAL,
        )
        val automatic = BlaBlaPublicProfileSnapshot(
            accountId = "a",
            profileUuid = uuidA,
            profileName = "Automático",
            photoUrl = "https://example.test/auto.jpg",
            about = "Auto",
        )
        val resolved = PublicDriverProfilePolicy.resolve(settings, uuidA, automatic)
        assertEquals("Meu nome", resolved.displayName)
        assertEquals("https://example.test/manual.jpg", resolved.photoUrl)
        assertEquals("Manual", resolved.about)
        assertEquals("11999999999", resolved.whatsapp)
        assertEquals("Pix", resolved.paymentInstructions)
    }

    @Test
    fun automaticModeUsesOnlySelectedConfirmedSnapshotAndOwnContactFields() {
        val settings = TripOnlineSettings(
            driverDisplayName = "Manual antigo",
            driverWhatsapp = "11999999999",
            paymentInstructions = "Pix",
            publicProfileMode = PublicDriverProfileMode.BLABLACAR,
        )
        val automatic = BlaBlaPublicProfileSnapshot(
            accountId = "b",
            profileUuid = uuidB,
            profileName = "Perfil B",
            photoUrl = "https://example.test/b.jpg",
            about = "Descrição B",
            rating = "4.8",
            reviewCount = 88,
            lastSyncedAtMillis = 40L,
        )
        val resolved = PublicDriverProfilePolicy.resolve(settings, uuidB, automatic)
        assertEquals("Perfil B", resolved.displayName)
        assertEquals("https://example.test/b.jpg", resolved.photoUrl)
        assertEquals("Descrição B", resolved.about)
        assertEquals("4.8", resolved.rating)
        assertEquals(88, resolved.reviewCount)
        assertEquals(uuidB, resolved.selectedProfileUuid)
        assertEquals("11999999999", resolved.whatsapp)
        assertEquals("Pix", resolved.paymentInstructions)
        assertTrue(resolved.automaticProfileAvailable)
    }

    @Test
    fun detailedReviewsArePreservedOnPartialRefreshOfSameUuid() {
        val previous = BlaBlaPublicProfileSnapshot(
            accountId = "a",
            profileUuid = uuidA,
            reviews = listOf(
                BlaBlaPublicReview(author = "Passageiro A", rating = "5", dateLabel = "2026", text = "Boa viagem"),
            ),
            lastSyncedAtMillis = 10L,
        )
        val result = BlaBlaPublicProfileModule.mergeConfirmed(
            accountId = "a",
            expectedUuid = uuidA,
            previous = previous,
            capture = BlaBlaPublicProfileCapture(
                observedProfileUuid = uuidA,
                profileName = "Nome atualizado",
                reviews = emptyList(),
            ),
            nowMillis = 20L,
        )
        val accepted = assertIs<BlaBlaPublicProfileMergeResult.Accepted>(result).snapshot
        assertEquals(1, accepted.reviews.size)
        assertEquals("Passageiro A", accepted.reviews.single().author)
    }

    @Test
    fun automaticModeCarriesOnlySelectedProfilesVerifiedReviews() {
        val settings = TripOnlineSettings(publicProfileMode = PublicDriverProfileMode.BLABLACAR)
        val automatic = BlaBlaPublicProfileSnapshot(
            accountId = "b",
            profileUuid = uuidB,
            profileName = "Perfil B",
            reviews = listOf(
                BlaBlaPublicReview(author = "Passageiro B", text = "Avaliação B"),
            ),
        )
        val resolved = PublicDriverProfilePolicy.resolve(settings, uuidB, automatic)
        assertEquals(uuidB, resolved.selectedProfileUuid)
        assertEquals(1, resolved.reviews.size)
        assertEquals("Passageiro B", resolved.reviews.single().author)
    }

    @Test
    fun hybridOverrideSurvivesAutomaticRefresh() {
        val settings = TripOnlineSettings(
            driverDisplayName = "Manual",
            driverPhotoUrl = "https://example.test/manual.jpg",
            driverPublicAbout = "Minha apresentação",
            publicProfileMode = PublicDriverProfileMode.HYBRID,
            publicProfileOverrideFields = setOf(PublicDriverProfileFields.ABOUT),
        )
        val refreshed = BlaBlaPublicProfileSnapshot(
            accountId = "a",
            profileUuid = uuidA,
            profileName = "Nome novo",
            photoUrl = "https://example.test/new.jpg",
            about = "Descrição automática nova",
            lastSyncedAtMillis = 100L,
        )
        val resolved = PublicDriverProfilePolicy.resolve(settings, uuidA, refreshed)
        assertEquals("Nome novo", resolved.displayName)
        assertEquals("https://example.test/new.jpg", resolved.photoUrl)
        assertEquals("Minha apresentação", resolved.about)
        assertEquals(setOf(PublicDriverProfileFields.ABOUT), resolved.overrideFields)
    }

    @Test
    fun removingOneHybridOverrideReturnsOnlyThatFieldToAutomatic() {
        val settings = TripOnlineSettings(
            driverPublicAbout = "Minha apresentação",
            driverPhotoUrl = "https://example.test/manual.jpg",
            publicProfileMode = PublicDriverProfileMode.HYBRID,
            publicProfileOverrideFields = setOf(PublicDriverProfileFields.PHOTO),
        )
        val automatic = BlaBlaPublicProfileSnapshot(
            accountId = "a",
            profileUuid = uuidA,
            photoUrl = "https://example.test/auto.jpg",
            about = "Automático",
        )
        val withPhotoOverride = PublicDriverProfilePolicy.resolve(settings, uuidA, automatic)
        assertEquals("https://example.test/manual.jpg", withPhotoOverride.photoUrl)
        assertEquals("Automático", withPhotoOverride.about)

        val backToAutomatic = PublicDriverProfilePolicy.resolve(
            settings.copy(publicProfileOverrideFields = emptySet()),
            uuidA,
            automatic,
        )
        assertEquals("https://example.test/auto.jpg", backToAutomatic.photoUrl)
        assertEquals("Automático", backToAutomatic.about)
    }
}
