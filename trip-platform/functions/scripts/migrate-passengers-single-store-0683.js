"use strict";

const crypto = require("crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

let db = null;
const MIGRATION_ID = "passenger_single_store_0683";
const POLICY_VERSION = 683;
const PASSENGER_COLLECTION = "passengers";
const ACCESS_SUBCOLLECTION = "driverAccess0683";
const LEGACY_IDENTITY_COLLECTIONS = [
  "passengerAccounts",
  "passengerDirectory0625",
  "passengerContactIndex0625",
  "driverPassengerAccess",
];
const AUTH_COLLECTIONS_TO_CLEAR = [
  "passengerSessions",
  "passengerAgendaViewSessions",
  "passengerPinGuards0624",
  "passengerReferralCodes",
];

function clean(value, max = 160) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function normalizedStoredContact(value) {
  const digits = clean(value, 40).replace(/\D/g, "");
  if (digits.length < 10) return "";
  if (digits.startsWith("55") && digits.length >= 12) return "+" + digits;
  return "+55" + digits.slice(-11);
}

function passengerRef(contact) {
  return db.collection(PASSENGER_COLLECTION).doc(sha256(contact));
}

function accessId(driverUsername, contact) {
  return clean(driverUsername, 80).toLowerCase() + "_" + sha256(contact).slice(0, 40);
}

function accessRef(driverUsername, contact) {
  return passengerRef(contact).collection(ACCESS_SUBCOLLECTION).doc(accessId(driverUsername, contact));
}

async function commitOps(ops) {
  for (let offset = 0; offset < ops.length; offset += 350) {
    const batch = db.batch();
    ops.slice(offset, offset + 350).forEach((op) => op(batch));
    await batch.commit();
  }
}

async function deleteCollection(name) {
  const snapshot = await db.collection(name).get();
  await commitOps(snapshot.docs.map((doc) => (batch) => batch.delete(doc.ref)));
  return snapshot.size;
}

async function runPassengerSingleStoreMigration0683(firestore) {
  db = firestore;
  const markerRef = db.collection("systemMigrations").doc(MIGRATION_ID);
  const marker = await markerRef.get();
  if (marker.exists && marker.data().completed === true) {
    return { migration: MIGRATION_ID, skipped: true, reason: "already_completed", summary: marker.data().summary || {} };
  }

  const startedAtMillis = Date.now();
  await markerRef.set({
    migration: MIGRATION_ID,
    policyVersion: POLICY_VERSION,
    completed: false,
    startedAtMillis,
    updatedAtMillis: startedAtMillis,
  }, { merge: true });

  const [
    legacyAccounts,
    legacyDirectory,
    legacyContactIndex,
    legacyAccess,
    existingCanonical,
    existingCanonicalAccess,
  ] = await Promise.all([
    db.collection("passengerAccounts").get(),
    db.collection("passengerDirectory0625").get(),
    db.collection("passengerContactIndex0625").get(),
    db.collection("driverPassengerAccess").get(),
    db.collection(PASSENGER_COLLECTION).get(),
    db.collectionGroup(ACCESS_SUBCOLLECTION).get(),
  ]);

  const directoryByPassengerId = new Map();
  legacyDirectory.docs.forEach((doc) => {
    const data = doc.data();
    const passengerId = clean(data.passengerId, 120);
    if (passengerId) directoryByPassengerId.set(passengerId, data);
  });

  const contactByPassengerId = new Map();
  legacyContactIndex.docs.forEach((doc) => {
    const data = doc.data();
    const passengerId = clean(data.passengerId, 120);
    const contact = normalizedStoredContact(data.passengerContact);
    if (passengerId && contact) contactByPassengerId.set(passengerId, contact);
  });

  const canonicalByContact = new Map();
  function addCandidate(rawContact, rawPassengerId, rawName, source, createdAtMillis = 0) {
    const contact = normalizedStoredContact(rawContact);
    const passengerId = clean(rawPassengerId, 120);
    if (!contact || !passengerId) return;
    const previous = canonicalByContact.get(contact);
    if (previous && previous.passengerId !== passengerId) {
      throw new Error("Passenger identity conflict for one contact; migration aborted before cleanup.");
    }
    const directory = directoryByPassengerId.get(passengerId) || {};
    canonicalByContact.set(contact, {
      passengerId,
      contact,
      displayName: clean(rawName || directory.displayName || (previous && previous.displayName), 120),
      source: clean(source, 80),
      createdAtMillis: Math.max(
        0,
        Number(createdAtMillis || directory.createdAtMillis || (previous && previous.createdAtMillis) || startedAtMillis),
      ),
    });
  }

  legacyAccounts.docs.forEach((doc) => {
    const data = doc.data();
    addCandidate(data.passengerContact, data.passengerId, data.displayName, "LEGACY_ACCOUNT_0683", data.createdAtMillis);
  });
  legacyContactIndex.docs.forEach((doc) => {
    const data = doc.data();
    addCandidate(data.passengerContact, data.passengerId, data.displayName, "LEGACY_CONTACT_INDEX_0683", data.createdAtMillis);
  });
  legacyAccess.docs.forEach((doc) => {
    const data = doc.data();
    addCandidate(data.passengerContact, data.passengerId, data.displayName, "LEGACY_ACCESS_0683", data.createdAtMillis);
  });
  existingCanonical.docs.forEach((doc) => {
    const data = doc.data();
    addCandidate(data.primaryContact || data.passengerContact, data.passengerId, data.displayName, "CANONICAL_PRE_MIGRATION_0683", data.createdAtMillis);
  });

  const unresolvedDirectory = [];
  directoryByPassengerId.forEach((data, passengerId) => {
    const contact = contactByPassengerId.get(passengerId);
    if (contact) {
      addCandidate(contact, passengerId, data.displayName, "LEGACY_DIRECTORY_0683", data.createdAtMillis);
    } else if (![...canonicalByContact.values()].some((item) => item.passengerId === passengerId)) {
      unresolvedDirectory.push(passengerId);
    }
  });
  if (unresolvedDirectory.length) {
    throw new Error("Canonical migration has passenger records without a contact; refusing destructive cleanup. unresolved=" + unresolvedDirectory.length);
  }

  const canonicalByPassengerId = new Map();
  canonicalByContact.forEach((item) => {
    const previous = canonicalByPassengerId.get(item.passengerId);
    const preferredContact = contactByPassengerId.get(item.passengerId);
    const itemPreferred = preferredContact && preferredContact === item.contact;
    const previousPreferred = previous && preferredContact && preferredContact === previous.contact;
    if (!previous || itemPreferred || (!previousPreferred && item.source === "CANONICAL_PRE_MIGRATION_0683")) {
      canonicalByPassengerId.set(item.passengerId, item);
    }
  });
  const canonicalContactByPassengerId = new Map(
    [...canonicalByPassengerId.values()].map((item) => [item.passengerId, item.contact]),
  );

  const writes = [];
  canonicalByPassengerId.forEach((item) => {
    writes.push((batch) => batch.set(passengerRef(item.contact), {
      passengerId: item.passengerId,
      primaryContact: item.contact,
      passengerContact: item.contact,
      displayName: item.displayName,
      source: "MIGRATION_0683",
      canonicalStoreVersion: POLICY_VERSION,
      passwordSalt: FieldValue.delete(),
      passwordHash: FieldValue.delete(),
      passwordFormat0625: FieldValue.delete(),
      pinAuthVersion0624: FieldValue.delete(),
      pinLastLoginAtMillis0624: FieldValue.delete(),
      lastLoginAtMillis0625: FieldValue.delete(),
      mustChangePassword: false,
      passwordClearedAtMillis0683: startedAtMillis,
      createdAtMillis: item.createdAtMillis,
      updatedAtMillis: startedAtMillis,
    }, { merge: true }));
  });

  legacyAccess.docs.forEach((doc) => {
    const data = doc.data();
    const storedContact = normalizedStoredContact(data.passengerContact);
    const driverUsername = clean(data.driverUsername, 80).toLowerCase();
    const passengerId = clean(data.passengerId, 120);
    const contact = canonicalContactByPassengerId.get(passengerId) || storedContact;
    if (!contact || !driverUsername || !passengerId) return;
    const legacyStatus = clean(data.status, 20).toUpperCase();
    const status = legacyStatus === "BLOCKED" ? "BLOCKED" : (legacyStatus === "MOVED" ? "MOVED" : "REVOKED");
    writes.push((batch) => batch.set(accessRef(driverUsername, contact), {
      driverUsername,
      passengerContact: contact,
      passengerId,
      displayName: clean(data.displayName, 120),
      status,
      agendaAdmin: false,
      referredByContact: clean(data.referredByContact, 40),
      referralCode: "",
      referralRewardGrantedAtMillis: Number(data.referralRewardGrantedAtMillis || 0),
      invitedAtMillis: 0,
      approvalPolicyVersion: POLICY_VERSION,
      approvedAtMillis: 0,
      approvalSource: "",
      revokedAtMillis: startedAtMillis,
      migrationSource0683: "LEGACY_DRIVER_ACCESS",
      createdAtMillis: Number(data.createdAtMillis || startedAtMillis),
      updatedAtMillis: startedAtMillis,
    }, { merge: true }));
  });

  const staleCanonicalAccessDeletes = [];
  existingCanonicalAccess.docs.forEach((doc) => {
    const data = doc.data();
    const passengerId = clean(data.passengerId, 120);
    const storedContact = normalizedStoredContact(data.passengerContact);
    const contact = canonicalContactByPassengerId.get(passengerId) || storedContact;
    const driverUsername = clean(data.driverUsername, 80).toLowerCase();
    if (!contact || !driverUsername || !passengerId) return;
    const destination = accessRef(driverUsername, contact);
    const legacyStatus = clean(data.status, 20).toUpperCase();
    const status = legacyStatus === "BLOCKED" ? "BLOCKED" : (legacyStatus === "MOVED" ? "MOVED" : "REVOKED");
    writes.push((batch) => batch.set(destination, {
      ...data,
      passengerContact: contact,
      passengerId,
      driverUsername,
      status,
      agendaAdmin: false,
      approvalPolicyVersion: POLICY_VERSION,
      approvedAtMillis: 0,
      approvalSource: "",
      invitedAtMillis: 0,
      revokedAtMillis: startedAtMillis,
      updatedAtMillis: startedAtMillis,
      migrationSource0683: "CANONICAL_PRE_RESET",
    }, { merge: true }));
    if (doc.ref.path !== destination.path) staleCanonicalAccessDeletes.push(doc.ref);
  });

  await commitOps(writes);

  const staleCanonicalParents = existingCanonical.docs.filter((doc) => {
    const data = doc.data();
    const passengerId = clean(data.passengerId, 120);
    const contact = canonicalContactByPassengerId.get(passengerId);
    return contact && doc.id !== sha256(contact);
  });
  await commitOps([
    ...staleCanonicalAccessDeletes.map((ref) => (batch) => batch.delete(ref)),
    ...staleCanonicalParents.map((doc) => (batch) => batch.delete(doc.ref)),
  ]);

  const authDeleted = {};
  for (const name of AUTH_COLLECTIONS_TO_CLEAR) {
    authDeleted[name] = await deleteCollection(name);
  }

  const legacyDeleted = {};
  for (const name of LEGACY_IDENTITY_COLLECTIONS) {
    legacyDeleted[name] = await deleteCollection(name);
  }

  const postPassengers = await db.collection(PASSENGER_COLLECTION).get();
  let credentialsRemaining = 0;
  postPassengers.docs.forEach((doc) => {
    const data = doc.data();
    if (clean(data.passwordSalt, 120) || clean(data.passwordHash, 256)) credentialsRemaining += 1;
  });
  if (credentialsRemaining !== 0) {
    throw new Error("Password cleanup verification failed. credentialsRemaining=" + credentialsRemaining);
  }

  const postAccess = await db.collectionGroup(ACCESS_SUBCOLLECTION).get();
  const activeAccessRemaining = postAccess.docs.filter((doc) => {
    const data = doc.data();
    const status = clean(data.status, 20).toUpperCase();
    return (status === "AUTHORIZED" || status === "ACTIVE") && Number(data.approvedAtMillis || 0) > 0;
  }).length;
  if (activeAccessRemaining !== 0) {
    throw new Error("Access reset verification failed. activeAccessRemaining=" + activeAccessRemaining);
  }

  const legacyRemaining = {};
  for (const name of LEGACY_IDENTITY_COLLECTIONS) {
    legacyRemaining[name] = (await db.collection(name).limit(1).get()).size;
  }
  const authRemaining = {};
  for (const name of AUTH_COLLECTIONS_TO_CLEAR) {
    authRemaining[name] = (await db.collection(name).limit(1).get()).size;
  }
  if (Object.values(legacyRemaining).some((count) => Number(count) !== 0)) {
    throw new Error("Legacy passenger stores were not fully cleared.");
  }
  if (Object.values(authRemaining).some((count) => Number(count) !== 0)) {
    throw new Error("Passenger authentication state was not fully cleared.");
  }

  const summary = {
    canonicalPassengers: postPassengers.size,
    migratedLegacyAccess: legacyAccess.size,
    canonicalAccessRecords: postAccess.size,
    passwordsRemaining: credentialsRemaining,
    activeAccessRemaining,
    legacyDeleted,
    authDeleted,
    legacyRemaining,
    authRemaining,
  };
  const completedAtMillis = Date.now();
  await markerRef.set({
    completed: true,
    completedAtMillis,
    updatedAtMillis: completedAtMillis,
    summary,
  }, { merge: true });

  return { migration: MIGRATION_ID, completed: true, summary };
}

module.exports = { runPassengerSingleStoreMigration0683 };

if (require.main === module) {
  initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || process.env.GCLOUD_PROJECT || "rota-certa-7ccc8",
  });
  const firestore = getFirestore();
  runPassengerSingleStoreMigration0683(firestore)
    .then((result) => console.log(JSON.stringify(result)))
    .catch(async (error) => {
      console.error(error && error.stack ? error.stack : error);
      try {
        await firestore.collection("systemMigrations").doc(MIGRATION_ID).set({
          completed: false,
          failedAtMillis: Date.now(),
          error: clean(error && error.message, 500),
          updatedAtMillis: Date.now(),
        }, { merge: true });
      } catch (_) {}
      process.exitCode = 1;
    });
}
