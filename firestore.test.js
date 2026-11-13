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

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

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

test("Unauthenticated user cannot read or write user profiles or routines", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("custom_commands").get());
  await assertFails(unauthDb.collection("app_config").doc("release").get());
});

test("Authenticated user can create and update their own profile, but not another user's", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();
  const validProfile = {
    userId: ALICE_UID,
    displayName: "S Irfan",
    wakeWord: "Hey Voice Master",
    assistantPersonality: "professional",
    preferredLanguage: "en-US",
    offlineModeOnly: false,
    analyticsConsent: true,
    cloudSyncEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set({ ...validProfile, userId: BOB_UID }));
  // Shadow update with ghost field must fail
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ isAdmin: true, updatedAt: pastTimestamp() }));
});

test("Authenticated user can CRUD their own custom routines, isolated from other users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  const routineRef = aliceDb.collection("users").doc(ALICE_UID).collection("custom_commands").doc("good_morning");
  await assertSucceeds(
    routineRef.set({
      userId: ALICE_UID,
      triggerPhrase: "Good morning",
      actions: ["READ_TIME", "READ_WEATHER", "READ_BATTERY"],
      customReply: "Good morning S Irfan",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("custom_commands").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("custom_commands").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("custom_commands").doc("good_morning").get());
});

test("Authenticated user can create and read notes/reminders and telemetry, telemetry updates blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes_reminders").doc("note_1");
  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      kind: "reminder",
      title: "Call Irfan",
      content: "Discuss project launch",
      dueAtMillis: 0,
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const metricRef = aliceDb.collection("users").doc(ALICE_UID).collection("telemetry_events").doc("ev_1");
  await assertSucceeds(
    metricRef.set({
      userId: ALICE_UID,
      commandCategory: "calling",
      language: "en-US",
      offlineHandled: true,
      latencyMs: 42,
      createdAt: ts,
    })
  );
  // Telemetry is append-only (no updates)
  await assertFails(metricRef.update({ latencyMs: 10 }));
});

test("App config readable by signed-in user, writable only by verified admin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", { email: "sirfanr671@gmail.com", email_verified: false }).firestore();
  const verifiedAdminDb = testEnv.authenticatedContext("admin_uid", { email: "sirfanr671@gmail.com", email_verified: true }).firestore();
  const ts = pastTimestamp();

  await assertFails(
    aliceDb.collection("app_config").doc("release").set({
      minSupportedVersion: "1.0",
      latestVersion: "1.0",
      maintenanceMode: false,
      announcement: "All systems normal",
      updatedBy: ALICE_UID,
      updatedAt: ts,
    })
  );

  // Email spoofing with email_verified: false must fail
  await assertFails(
    spoofedAdminDb.collection("app_config").doc("release").set({
      minSupportedVersion: "1.0",
      latestVersion: "1.0",
      maintenanceMode: false,
      announcement: "Spoof",
      updatedBy: "spoof_uid",
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    verifiedAdminDb.collection("app_config").doc("release").set({
      minSupportedVersion: "1.0",
      latestVersion: "1.1",
      maintenanceMode: false,
      announcement: "Voice Master AI 1.1 live",
      updatedBy: "admin_uid",
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("app_config").doc("release").get());
  await assertFails(aliceDb.collection("app_config").get());
});
