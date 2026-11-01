const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validSaveSlotPayload(userId, now) {
  return {
    userId,
    slotName: "Slot 1 - Bunker Survival",
    highestWave: 12,
    totalKills: 450,
    points: 8200,
    loadoutSummary: "Pistol Lv2, Shotgun Lv3, Railgun Lv1",
    encryptedPayload: "AESGCM_BASE64_PAYLOAD_SAMPLE_12345",
    deviceTag: "MyBoy-Android-Handheld",
    createdAt: now,
    updatedAt: now,
  };
}

function validControllerProfilePayload(userId, now) {
  return {
    userId,
    profileName: "MyBoy Tactical GBA",
    dpadScale: 115,
    buttonOpacity: 85,
    keyMapCode: "DPAD:LEFT_BOTTOM|A:FIRE|B:ALT_BARREL|L:PREV_WPN|R:NEXT_WPN",
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read or list save_slots", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("save_slots").get()
  );
});

test("2. Authenticated user can create and read their own valid SaveSlot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").doc("slot_1");
  await assertSucceeds(docRef.set(validSaveSlotPayload(ALICE_UID, now)));
  await assertSucceeds(docRef.get());
});

test("3. Authenticated user can query their own save_slots with aligned userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").doc("slot_1").set(validSaveSlotPayload(ALICE_UID, now));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").where("userId", "==", ALICE_UID).get()
  );
});

test("4. Authenticated user cannot read another user's SaveSlot", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).collection("save_slots").doc("slot_bob").set(validSaveSlotPayload(BOB_UID, now));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("save_slots").doc("slot_bob").get()
  );
});

test("5. Identity spoofing on SaveSlot create is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").doc("slot_spoof").set(validSaveSlotPayload(BOB_UID, now))
  );
});

test("6. Shadow field injection on SaveSlot create and update is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").doc("slot_shadow");
  await assertFails(
    docRef.set({ ...validSaveSlotPayload(ALICE_UID, now), isAdmin: true })
  );
  await assertSucceeds(docRef.set(validSaveSlotPayload(ALICE_UID, now)));
  await assertFails(
    docRef.update({ highestWave: 15, ghostField: "hacked", updatedAt: new Date() })
  );
});

test("7. Valid update on SaveSlot succeeds while immutable createdAt mutation fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_slots").doc("slot_upd");
  await assertSucceeds(docRef.set(validSaveSlotPayload(ALICE_UID, now)));
  await assertSucceeds(
    docRef.update({ highestWave: 20, totalKills: 900, updatedAt: new Date() })
  );
  await assertFails(
    docRef.update({ createdAt: new Date(), updatedAt: new Date() })
  );
});

test("8. ControllerProfile CRUD and bounds enforcement work properly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("controller_profiles").doc("prof_1");
  await assertSucceeds(profileRef.set(validControllerProfilePayload(ALICE_UID, now)));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("controller_profiles").doc("prof_bad").set({
      ...validControllerProfilePayload(ALICE_UID, now),
      dpadScale: 999,
    })
  );
});
