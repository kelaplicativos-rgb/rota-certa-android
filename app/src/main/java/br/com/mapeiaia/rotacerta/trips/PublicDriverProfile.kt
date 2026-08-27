package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class PublicDriverProfileMode {
    BLABLACAR,
    MANUAL,
    HYBRID,
}

internal object PublicDriverProfileFields {
    const val NAME = "name"
    const val PHOTO = "photo"
    const val ABOUT = "about"
    const val RATING = "rating"
    const val REVIEW_COUNT = "review_count"
    const val BADGE = "badge"
    const val VEHICLE = "vehicle"
    const val VEHICLE_COLOR = "vehicle_color"
    const val AMENITIES = "amenities"
    const val PREFERENCES = "preferences"

    val profileControlled = setOf(
        NAME, PHOTO, ABOUT, RATING, REVIEW_COUNT, BADGE,
        VEHICLE, VEHICLE_COLOR, AMENITIES, PREFERENCES,
    )
}

@Serializable
data class BlaBlaPublicProfileSnapshot(
    val accountId: String,
    val profileUuid: String,
    val profileName: String = "",
    val photoUrl: String = "",
    val about: String = "",
    val rating: String = "",
    val reviewCount: Int? = null,
    val badge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val amenities: String = "",
    val preferences: String = "",
    val identityVerified: Boolean = true,
    val lastSyncedAtMillis: Long = System.currentTimeMillis(),
)

internal data class BlaBlaPublicProfileCapture(
    val observedProfileUuid: String,
    val profileName: String = "",
    val photoUrl: String = "",
    val about: String = "",
    val rating: String = "",
    val reviewCount: Int? = null,
    val badge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val amenities: String = "",
    val preferences: String = "",
)

internal sealed class BlaBlaPublicProfileMergeResult {
    data class Accepted(val snapshot: BlaBlaPublicProfileSnapshot) : BlaBlaPublicProfileMergeResult()
    data class RejectedIdentity(val expectedUuid: String, val observedUuid: String) : BlaBlaPublicProfileMergeResult()
}

internal object BlaBlaPublicProfileModule {
    fun mergeConfirmed(
        accountId: String,
        expectedUuid: String,
        previous: BlaBlaPublicProfileSnapshot?,
        capture: BlaBlaPublicProfileCapture,
        nowMillis: Long = System.currentTimeMillis(),
    ): BlaBlaPublicProfileMergeResult {
        val expected = expectedUuid.trim().lowercase()
        val observed = capture.observedProfileUuid.trim().lowercase()
        if (expected.isBlank() || observed.isBlank() || expected != observed) {
            return BlaBlaPublicProfileMergeResult.RejectedIdentity(expected, observed)
        }
        val prior = previous?.takeIf {
            it.accountId == accountId && it.profileUuid.equals(expected, ignoreCase = true)
        }
        fun keep(incoming: String, old: String): String = incoming.trim().takeIf(String::isNotEmpty) ?: old
        val snapshot = BlaBlaPublicProfileSnapshot(
            accountId = accountId,
            profileUuid = expected,
            profileName = keep(capture.profileName, prior?.profileName.orEmpty()),
            photoUrl = capture.photoUrl.trim().takeIf { it.startsWith("https://") } ?: prior?.photoUrl.orEmpty(),
            about = keep(capture.about, prior?.about.orEmpty()),
            rating = keep(capture.rating, prior?.rating.orEmpty()),
            reviewCount = capture.reviewCount ?: prior?.reviewCount,
            badge = keep(capture.badge, prior?.badge.orEmpty()),
            vehicleMakeModel = keep(capture.vehicleMakeModel, prior?.vehicleMakeModel.orEmpty()),
            vehicleColor = keep(capture.vehicleColor, prior?.vehicleColor.orEmpty()),
            amenities = keep(capture.amenities, prior?.amenities.orEmpty()),
            preferences = keep(capture.preferences, prior?.preferences.orEmpty()),
            identityVerified = true,
            lastSyncedAtMillis = nowMillis,
        )
        return BlaBlaPublicProfileMergeResult.Accepted(snapshot)
    }
}

class BlaBlaPublicProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = scope.key(KEY_SNAPSHOTS)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(accountId: String): BlaBlaPublicProfileSnapshot? =
        all().firstOrNull { it.accountId == accountId }

    fun all(): List<BlaBlaPublicProfileSnapshot> = runCatching {
        json.decodeFromString<List<BlaBlaPublicProfileSnapshot>>(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    internal fun mergeConfirmed(
        account: BlaBlaDynamicAccount,
        capture: BlaBlaPublicProfileCapture,
    ): BlaBlaPublicProfileMergeResult {
        val result = BlaBlaPublicProfileModule.mergeConfirmed(
            accountId = account.id,
            expectedUuid = account.profileUuid.orEmpty(),
            previous = read(account.id),
            capture = capture,
        )
        when (result) {
            is BlaBlaPublicProfileMergeResult.Accepted -> {
                val current = all().filterNot { it.accountId == account.id }
                prefs.edit().putString(key, json.encodeToString(listOf(result.snapshot) + current)).apply()
                UnifiedDebugEventStore.record(
                    "PROFILE_FIELDS_UPDATED",
                    appContext.packageName,
                    "accountId=${account.id} profileUuidPresent=true lastSync=${result.snapshot.lastSyncedAtMillis}",
                )
            }
            is BlaBlaPublicProfileMergeResult.RejectedIdentity -> {
                UnifiedDebugEventStore.record(
                    "PROFILE_UUID_MISMATCH",
                    appContext.packageName,
                    "accountId=${account.id} expectedUuidPresent=${result.expectedUuid.isNotBlank()} observedUuidPresent=${result.observedUuid.isNotBlank()}",
                )
            }
        }
        return result
    }

    companion object {
        private const val PREFS = "rota_certa_public_driver_profiles_v1"
        private const val KEY_SNAPSHOTS = "blablacar_public_profile_snapshots"
    }
}

@Serializable
data class ResolvedPublicDriverProfile(
    val displayName: String = "",
    val whatsapp: String = "",
    val photoUrl: String = "",
    val about: String = "",
    val rating: String = "",
    val reviewCount: Int? = null,
    val badge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val amenities: String = "",
    val preferences: String = "",
    val paymentInstructions: String = "",
    val sourceMode: PublicDriverProfileMode = PublicDriverProfileMode.MANUAL,
    val selectedProfileUuid: String = "",
    val automaticProfileAvailable: Boolean = false,
    val automaticProfileLastSyncedAtMillis: Long? = null,
    val overrideFields: Set<String> = emptySet(),
) {
    companion object {
        fun manual(settings: TripOnlineSettings) = ResolvedPublicDriverProfile(
            displayName = settings.driverDisplayName,
            whatsapp = settings.driverWhatsapp,
            photoUrl = settings.driverPhotoUrl,
            about = settings.driverPublicAbout,
            rating = settings.driverPublicRating,
            reviewCount = settings.driverPublicReviewCount.takeIf { it > 0 },
            badge = settings.driverPublicBadge,
            vehicleMakeModel = settings.vehicleMakeModel,
            vehicleColor = settings.vehicleColor,
            amenities = settings.vehicleAmenities,
            preferences = settings.driverPreferences,
            paymentInstructions = settings.paymentInstructions,
            sourceMode = PublicDriverProfileMode.MANUAL,
        )
    }
}

internal object PublicDriverProfilePolicy {
    fun resolve(
        settings: TripOnlineSettings,
        selectedProfileUuid: String,
        automatic: BlaBlaPublicProfileSnapshot?,
    ): ResolvedPublicDriverProfile {
        if (settings.publicProfileMode == PublicDriverProfileMode.MANUAL) {
            return ResolvedPublicDriverProfile.manual(settings)
        }
        val overrides = settings.publicProfileOverrideFields.intersect(PublicDriverProfileFields.profileControlled)
        fun text(field: String, automaticValue: String, manualValue: String): String = when (settings.publicProfileMode) {
            PublicDriverProfileMode.MANUAL -> manualValue
            PublicDriverProfileMode.BLABLACAR -> automaticValue
            PublicDriverProfileMode.HYBRID -> if (field in overrides) manualValue else automaticValue
        }
        fun count(automaticValue: Int?, manualValue: Int): Int? = when (settings.publicProfileMode) {
            PublicDriverProfileMode.MANUAL -> manualValue.takeIf { it > 0 }
            PublicDriverProfileMode.BLABLACAR -> automaticValue
            PublicDriverProfileMode.HYBRID -> if (PublicDriverProfileFields.REVIEW_COUNT in overrides) {
                manualValue.takeIf { it > 0 }
            } else automaticValue
        }

        return ResolvedPublicDriverProfile(
            displayName = text(PublicDriverProfileFields.NAME, automatic?.profileName.orEmpty(), settings.driverDisplayName),
            whatsapp = settings.driverWhatsapp,
            photoUrl = text(PublicDriverProfileFields.PHOTO, automatic?.photoUrl.orEmpty(), settings.driverPhotoUrl),
            about = text(PublicDriverProfileFields.ABOUT, automatic?.about.orEmpty(), settings.driverPublicAbout),
            rating = text(PublicDriverProfileFields.RATING, automatic?.rating.orEmpty(), settings.driverPublicRating),
            reviewCount = count(automatic?.reviewCount, settings.driverPublicReviewCount),
            badge = text(PublicDriverProfileFields.BADGE, automatic?.badge.orEmpty(), settings.driverPublicBadge),
            vehicleMakeModel = text(PublicDriverProfileFields.VEHICLE, automatic?.vehicleMakeModel.orEmpty(), settings.vehicleMakeModel),
            vehicleColor = text(PublicDriverProfileFields.VEHICLE_COLOR, automatic?.vehicleColor.orEmpty(), settings.vehicleColor),
            amenities = text(PublicDriverProfileFields.AMENITIES, automatic?.amenities.orEmpty(), settings.vehicleAmenities),
            preferences = text(PublicDriverProfileFields.PREFERENCES, automatic?.preferences.orEmpty(), settings.driverPreferences),
            paymentInstructions = settings.paymentInstructions,
            sourceMode = settings.publicProfileMode,
            selectedProfileUuid = selectedProfileUuid,
            automaticProfileAvailable = automatic != null,
            automaticProfileLastSyncedAtMillis = automatic?.lastSyncedAtMillis,
            overrideFields = if (settings.publicProfileMode == PublicDriverProfileMode.HYBRID) overrides else emptySet(),
        )
    }
}

class PublicDriverProfileResolver(context: Context) {
    private val registry = BlaBlaDynamicAccountRegistry(context)
    private val snapshots = BlaBlaPublicProfileStore(context)

    fun resolve(settings: TripOnlineSettings): ResolvedPublicDriverProfile {
        val account = registry.get(settings.selectedPublicProfileAccountId)
        val automatic = account?.let { snapshots.read(it.id) }?.takeIf { snapshot ->
            snapshot.identityVerified &&
                !account.profileUuid.isNullOrBlank() &&
                snapshot.profileUuid.equals(account.profileUuid, ignoreCase = true)
        }
        return PublicDriverProfilePolicy.resolve(
            settings = settings,
            selectedProfileUuid = account?.profileUuid.orEmpty(),
            automatic = automatic,
        )
    }
}
