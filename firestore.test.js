const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_001";
const BOOTSTRAP_EMAIL = "developinggamex@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

async function seedAdminDoc(uid) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(uid).set({ enabled: true });
  });
}

test("Unauthenticated users cannot read or write catalog, drafts, admins, or user records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("apps").where("published", "==", true).get());
  await assertFails(unauthDb.collection("appDrafts").get());
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("apps").doc("app_1").set({
      name: "Bad",
      category: "Tools",
      description: "Desc",
      published: true,
      downloadUrl: "https://example.com/app.apk",
      createdBy: ALICE_UID,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing with unverified email_verified=false does not grant admin access", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: false,
    })
    .firestore();

  const now = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("apps").doc("app_spoof").set({
      name: "SpoofApp",
      category: "Tools",
      description: "Spoofed admin attempt",
      published: true,
      downloadUrl: "https://example.com/app.apk",
      createdBy: "spoof_uid",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(spoofDb.collection("appDrafts").get());
});

test("Bootstrapped verified admin and /admins doc admin can publish, update, draft, and delete listings", async () => {
  const bootstrapDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();

  const created = new Date(Date.now() - 5000);
  await assertSucceeds(
    bootstrapDb.collection("apps").doc("app_1").set({
      name: "Pixel Launcher Mod",
      category: "Personalization",
      developer: "AppNest Studio",
      version: "2.1.0",
      tagline: "Clean adaptive launcher",
      description: "Full description for AppNest listing.",
      downloadUrl: "https://cdn.example.com/pixel-mod.apk",
      iconUrl: "https://cdn.example.com/icon.png",
      screenshots: ["https://cdn.example.com/s1.png"],
      sizeBytes: 15728640,
      rating: 4.8,
      reviewCount: 120,
      downloadCount: 5400,
      featured: true,
      published: true,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bootstrapDb.collection("apps").doc("app_1").update({
      version: "2.2.0",
      downloadCount: 5500,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    bootstrapDb.collection("apps").doc("app_1").update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    bootstrapDb.collection("apps").doc("app_1").update({
      version: "2.3.0",
      updatedAt: new Date(Date.now() + 120000),
    })
  );

  await assertFails(
    bootstrapDb.collection("apps").doc("app_bad_url").set({
      name: "Insecure",
      category: "Tools",
      description: "Bad URL",
      downloadUrl: "http://example.com/app.apk",
      published: true,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bootstrapDb.collection("appDrafts").doc("draft_1").set({
      name: "Beta Notes",
      category: "Productivity",
      description: "Draft notes app",
      published: false,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(bootstrapDb.collection("appDrafts").get());
  await assertSucceeds(bootstrapDb.collection("apps").doc("app_1").delete());
});

test("Regular signed-in user can read published apps via filtered query, cannot write apps/drafts/admins", async () => {
  await seedAdminDoc(ADMIN_UID);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminDb.collection("apps").doc("pub_1").set({
      name: "Nova Reader",
      category: "Books",
      description: "Offline EPUB reader",
      driveUrl: "https://drive.google.com/file/d/1234567890abcdef/view",
      published: true,
      createdBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("apps").where("published", "==", true).get()
  );
  await assertFails(aliceDb.collection("appDrafts").get());
  await assertFails(
    aliceDb.collection("apps").doc("pub_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("admins").doc(ALICE_UID).set({ enabled: true }));
  await assertSucceeds(aliceDb.collection("admins").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("admins").doc(ADMIN_UID).get());
});

test("User profile and trackedApps enforce strict per-user isolation and schema parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      autoCheckUpdates: true,
      wifiOnlyDownloads: false,
      notificationsEnabled: true,
      recentSearches: ["nova"],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trackedApps")
      .doc("pub_1")
      .set({
        userId: ALICE_UID,
        name: "Nova Reader",
        version: "1.0.0",
        bytes: 4200000,
        status: "DOWNLOADED",
        localFileName: "Nova-Reader.apk",
        downloadedAt: Date.now() - 2000,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trackedApps")
      .doc("pub_1")
      .update({
        status: "INSTALLED",
        installedAt: Date.now() - 500,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trackedApps").get()
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trackedApps").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trackedApps")
      .doc("pub_1")
      .update({
        status: "INSTALLED",
        updatedAt: new Date(Date.now() - 500),
      })
  );
});
