"use strict";

const PUBLIC_PROFILE_OVERRIDE_FIELDS = new Set([
  "name", "photo", "about", "rating", "review_count", "badge",
  "vehicle", "vehicle_color", "amenities", "preferences",
]);

const PROFILE_FIELD_MAP = {
  name: "displayName",
  photo: "driverPhotoUrl",
  about: "driverPublicAbout",
  rating: "driverPublicRating",
  review_count: "driverPublicReviewCount",
  badge: "driverPublicBadge",
  vehicle: "vehicleMakeModel",
  vehicle_color: "vehicleColor",
  amenities: "vehicleAmenities",
  preferences: "driverPreferences",
};

function clean(value, max = 240) {
  return String(value || "").trim().slice(0, max);
}

function normalizeMode(value) {
  const mode = clean(value, 24).toUpperCase();
  return new Set(["BLABLACAR", "MANUAL", "HYBRID"]).has(mode) ? mode : "MANUAL";
}

function normalizeUuid(value) {
  const uuid = clean(value, 80).toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid) ? uuid : "";
}

function normalizeOverrides(value) {
  if (!Array.isArray(value)) return [];
  return Array.from(new Set(value
    .map((item) => clean(item, 40))
    .filter((item) => PUBLIC_PROFILE_OVERRIDE_FIELDS.has(item))))
    .slice(0, PUBLIC_PROFILE_OVERRIDE_FIELDS.size);
}

function resolvedFields(body) {
  const source = body || {};
  return {
    displayName: clean(source.driverDisplayName, 120),
    driverPhotoUrl: clean(source.driverPhotoUrl, 500).startsWith("https://") ? clean(source.driverPhotoUrl, 500) : "",
    driverPublicAbout: clean(source.driverPublicAbout, 320),
    driverPublicRating: clean(source.driverPublicRating, 20),
    driverPublicReviewCount: Math.max(0, Math.min(9999999, Number(source.driverPublicReviewCount || 0) || 0)),
    driverPublicBadge: clean(source.driverPublicBadge, 80),
    vehicleMakeModel: clean(source.vehicleMakeModel, 120),
    vehicleColor: clean(source.vehicleColor, 60),
    vehicleAmenities: clean(source.vehicleAmenities, 240),
    driverPreferences: clean(source.driverPreferences, 240),
  };
}

function applyOverrides(target, resolved, overrides) {
  overrides.forEach((field) => {
    const key = PROFILE_FIELD_MAP[field];
    if (key) target[key] = resolved[key];
  });
}

function clearNonOverridden(target, resolved, overrides) {
  Object.entries(PROFILE_FIELD_MAP).forEach(([field, key]) => {
    if (overrides.includes(field)) {
      target[key] = resolved[key];
    } else {
      target[key] = key === "driverPublicReviewCount" ? 0 : "";
    }
  });
}

function buildProfileUpdate({ body, current = {}, driverWhatsapp = "" }) {
  const mode = normalizeMode(body && body.publicProfileMode);
  const selectedUuid = normalizeUuid(body && body.selectedPublicProfileUuid);
  const lastSyncedAtMillis = Math.max(0, Number(body && body.publicProfileLastSyncedAtMillis || 0) || 0);
  const overrides = normalizeOverrides(body && body.publicProfileOverrideFields);
  const resolved = resolvedFields(body);
  const update = {
    driverWhatsapp,
    paymentInstructions: clean(body && body.paymentInstructions, 240),
    publicProfileMode: mode,
    publicProfileOverrideFields: overrides,
  };

  if (mode === "MANUAL") {
    Object.assign(update, resolved, {
      selectedPublicProfileUuid: "",
      publicProfileLastSyncedAtMillis: 0,
    });
    return { ok: true, mode, selectedUuid: "", lastSyncedAtMillis: 0, overrides, update };
  }

  if (!selectedUuid) {
    return { ok: false, code: "public_profile_identity_required", mode, selectedUuid: "", lastSyncedAtMillis, overrides, update: null };
  }

  const previousUuid = normalizeUuid(current.selectedPublicProfileUuid);
  update.selectedPublicProfileUuid = selectedUuid;
  if (lastSyncedAtMillis > 0) {
    Object.assign(update, resolved, { publicProfileLastSyncedAtMillis: lastSyncedAtMillis });
  } else if (previousUuid === selectedUuid) {
    if (mode === "HYBRID") applyOverrides(update, resolved, overrides);
  } else {
    clearNonOverridden(update, resolved, mode === "HYBRID" ? overrides : []);
    update.publicProfileLastSyncedAtMillis = 0;
  }
  return { ok: true, mode, selectedUuid, lastSyncedAtMillis, overrides, update };
}

module.exports = {
  PUBLIC_PROFILE_OVERRIDE_FIELDS,
  PROFILE_FIELD_MAP,
  normalizeMode,
  normalizeUuid,
  normalizeOverrides,
  resolvedFields,
  buildProfileUpdate,
};
