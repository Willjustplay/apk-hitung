const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_sakuhari_123";
const BOB_UID = "bob_sakuhari_456";

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

// --- UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated user cannot read or write any subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("wallets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("budgets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("wallets").doc("wallet_cash").set({
      name: "Tunai",
      type: "CASH",
      balance: 500000,
      colorHex: "#0F6A53",
      createdAt: now,
      updatedAt: now,
    })
  );
});

// --- WALLETS ---

test("Owner can create, list, update, and delete wallets; rejects ghost fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const walletRef = aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("wallet_cash");
  await assertSucceeds(
    walletRef.set({
      name: "Tunai",
      type: "CASH",
      balance: 250000,
      colorHex: "#0F6A53",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (empty or populated)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("wallets").get());

  // Partial balance adjustment
  await assertSucceeds(
    walletRef.update({
      balance: 300000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Full wallet details update
  await assertSucceeds(
    walletRef.update({
      name: "Dompet Tunai",
      type: "CASH",
      balance: 300000,
      colorHex: "#1E8468",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    walletRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field / @DocumentId 'id' inside data
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("wallet_bad").set({
      id: "wallet_bad",
      name: "Bank",
      type: "BANK",
      balance: 0,
      colorHex: "#0F6A53",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("wallet_future").set({
      name: "Future",
      type: "BANK",
      balance: 0,
      colorHex: "#0F6A53",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(walletRef.delete());
});

// --- TRANSACTIONS & WALLET REFERENTIAL INTEGRITY ---

test("Transactions require existing wallet, validate positive amount, and allow owner edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Create wallet first
  const walletRef = aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("wallet_bca");
  await assertSucceeds(
    walletRef.set({
      name: "Bank BCA",
      type: "BANK",
      balance: 1000000,
      colorHex: "#1D5E7A",
      createdAt: created,
      updatedAt: created,
    })
  );

  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1");
  await assertSucceeds(
    txRef.set({
      type: "EXPENSE",
      amount: 45000,
      category: "Makanan",
      walletId: "wallet_bca",
      walletName: "Bank BCA",
      note: "Makan siang nasi padang",
      dateKey: "2026-11-22",
      monthKey: "2026-11",
      timeLabel: "12:30",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject transaction pointing to non-existent wallet
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_orphan").set({
      type: "EXPENSE",
      amount: 10000,
      category: "Makanan",
      walletId: "missing_wallet",
      walletName: "Ghost",
      note: "",
      dateKey: "2026-11-22",
      monthKey: "2026-11",
      timeLabel: "12:30",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject zero/negative amount
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_zero").set({
      type: "EXPENSE",
      amount: 0,
      category: "Makanan",
      walletId: "wallet_bca",
      walletName: "Bank BCA",
      note: "",
      dateKey: "2026-11-22",
      monthKey: "2026-11",
      timeLabel: "12:30",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    txRef.update({
      amount: 50000,
      note: "Makan siang + es teh",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Query transactions
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertSucceeds(txRef.delete());
});

// --- MONTHLY BUDGETS ---

test("Owner can create, update limit, list, and delete monthly budgets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const budgetRef = aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("2026-11_makanan");

  await assertSucceeds(
    budgetRef.set({
      monthKey: "2026-11",
      category: "Makanan",
      limitAmount: 1500000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update limitAmount only
  await assertSucceeds(
    budgetRef.update({
      limitAmount: 1750000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating category or monthKey on existing budget
  await assertFails(
    budgetRef.update({
      category: "Belanja",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("budgets").get());
  await assertSucceeds(budgetRef.delete());
});

// --- TASKS & DAILY SCHEDULES ---

test("Owner can create tasks (with or without time), toggle completion, edit, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Untimed task (startTime & endTime omitted, mirroring .filterValues { it != null })
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  await assertSucceeds(
    taskRef.set({
      title: "Bayar tagihan listrik bulanan",
      notes: "Cek meteran sebelum transfer",
      dateKey: "2026-11-22",
      priority: "HIGH",
      category: "Keuangan",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Timed schedule entry
  const scheduleRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("sched_1");
  await assertSucceeds(
    scheduleRef.set({
      title: "Rapat evaluasi proyek",
      notes: "Siapkan laporan mingguan",
      dateKey: "2026-11-22",
      startTime: "09:30",
      endTime: "10:30",
      priority: "MEDIUM",
      category: "Kerja",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle completed action
  await assertSucceeds(
    taskRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Edit details action
  await assertSucceeds(
    scheduleRef.update({
      title: "Rapat evaluasi proyek Q4",
      startTime: "10:00",
      endTime: "11:00",
      priority: "HIGH",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid priority enum
  await assertFails(
    taskRef.update({
      priority: "URGENT",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertSucceeds(taskRef.delete());
});

// --- CROSS-USER ISOLATION ---

test("Cross-user access is strictly denied across all collections", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("wallets").doc("bob_w").set({
      name: "Bob Cash",
      type: "CASH",
      balance: 100000,
      colorHex: "#0F6A53",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t").set({
      title: "Bob Secret Task",
      notes: "",
      dateKey: "2026-11-22",
      priority: "LOW",
      category: "Pribadi",
      completed: false,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("wallets").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("wallets").doc("bob_w").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("wallets").doc("bob_w").update({
      balance: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t").delete());
});
