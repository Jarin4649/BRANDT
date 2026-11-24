const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-motora-abb5c83d-3e78-4cd2-8989-920543fb0dd6";
const ALICE_UID = "alice_motora_1";
const BOB_UID = "bob_motora_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVehiclePayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    manufacturer: "Volvo",
    model: "EX30",
    modelYear: 2025,
    trim: "Twin Motor Performance",
    bodyStyle: "CROSSOVER",
    engineType: "Dual-Motor Electric (BEV)",
    engineDisplacement: "N/A (Electric)",
    transmission: "Single-Speed Direct Drive",
    fuelType: "Electric",
    mileage: 4250,
    mileageUnit: "km",
    market: "United States",
    photoUri: "",
    primary: true,
    specSource: "DEMO_CATALOG_PREFILL",
    verificationStatus: "DEMO_DATA_UNVERIFIED",
    unknownFields: [],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validUserProfilePayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    displayName: "Alex Mercer",
    preferredMarket: "United States",
    preferredMileageUnit: "km",
    primaryVehicleId: "veh_1",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Unauthenticated caller cannot read or write user profiles or vehicles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vehicles").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_1").set(
      validVehiclePayload(ALICE_UID)
    )
  );
});

test("Owner can create, list, update, set primary, and delete their own vehicles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const vehRef = profileRef.collection("vehicles").doc("veh_1");

  await assertSucceeds(profileRef.set(validUserProfilePayload(ALICE_UID)));
  await assertSucceeds(vehRef.set(validVehiclePayload(ALICE_UID)));

  // List vehicles scoped to owner
  await assertSucceeds(profileRef.collection("vehicles").get());

  // Partial update: mileage
  await assertSucceeds(
    vehRef.update({
      mileage: 5100,
      mileageUnit: "km",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial update: primary toggle
  await assertSucceeds(
    vehRef.update({
      primary: false,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Partial update: multi-angle studio photos & AI summary
  await assertSucceeds(
    vehRef.update({
      anglePhotos: {
        FRONT: "/data/user/0/com.aistudio.motora.k4m9vx/files/front.jpg",
        REAR: "/data/user/0/com.aistudio.motora.k4m9vx/files/rear.jpg",
      },
      aiInspectionSummary: "Crystal white metallic crossover captured from front and rear angles.",
      updatedAt: new Date(Date.now() - 350),
    })
  );

  // Full profile edit preserving immutable createdAt & userId
  await assertSucceeds(
    vehRef.update({
      trim: "Single Motor Extended Range",
      specSource: "USER_ENTERED",
      verificationStatus: "UNVERIFIED_USER_DATA",
      unknownFields: ["engineDisplacement"],
      updatedAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(vehRef.delete());
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's vehicles", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validUserProfilePayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("vehicles")
      .doc("bob_veh_1")
      .set(validVehiclePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vehicles").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("vehicles").doc("bob_veh_1").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("vehicles")
      .doc("bob_veh_1")
      .update({ mileage: 9999, mileageUnit: "km", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("vehicles")
      .doc("alice_injected")
      .set(validVehiclePayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("vehicles").doc("bob_veh_1").delete()
  );
});

test("Validation guards reject ghost fields, invalid years, negative mileage, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vehicles = aliceDb.collection("users").doc(ALICE_UID).collection("vehicles");

  // Reject @DocumentId "id" key inside payload
  await assertFails(
    vehicles.doc("veh_bad_id").set({
      id: "veh_bad_id",
      ...validVehiclePayload(ALICE_UID),
    })
  );

  // Reject ghost field
  await assertFails(
    vehicles.doc("veh_ghost").set({
      ...validVehiclePayload(ALICE_UID),
      oemVerified: true,
    })
  );

  // Reject invalid model year
  await assertFails(
    vehicles.doc("veh_year").set(validVehiclePayload(ALICE_UID, { modelYear: 1800 }))
  );

  // Reject negative mileage
  await assertFails(
    vehicles.doc("veh_miles").set(validVehiclePayload(ALICE_UID, { mileage: -10 }))
  );

  // Reject future timestamp
  await assertFails(
    vehicles.doc("veh_future").set(
      validVehiclePayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Create valid vehicle, then reject immutable createdAt or userId change
  await assertSucceeds(vehicles.doc("veh_valid").set(validVehiclePayload(ALICE_UID)));
  await assertFails(
    vehicles.doc("veh_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    vehicles.doc("veh_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("DiagnosisLogs: owner CRUD succeeds while unauthenticated, cross-user, and ghost writes fail", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceVehicleRef = aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_mirage");
  await assertSucceeds(aliceVehicleRef.set(validVehiclePayload(ALICE_UID)));

  const logsRef = aliceVehicleRef.collection("diagnosisLogs");
  const validLog = {
    userId: ALICE_UID,
    vehicleId: "veh_mirage",
    userPrompt: "ได้ยินเสียงเอี๊ยดๆ ตอนเหยียบเบรกช่วงเช้า",
    aiSummary: "เสียงเอี๊ยดเบาๆ ตอนเบรกมักเกิดจากผ้าเบรกมีฝุ่นหรือใกล้หมด เหมือนยางลบถูโต๊ะ",
    attachmentType: "AUDIO",
    attachmentLabel: "car_sound_8s.m4a",
    modelCommonality: "พบบ่อยในรุ่นนี้",
    followUpQuestion: "เสียงดังมาจากล้อหน้าหรือล้อหลังครับ?",
    resolved: false,
    createdAt: now,
    updatedAt: now,
  };

  // Owner create, list, toggle resolved, and delete succeed
  await assertSucceeds(logsRef.doc("log_1").set(validLog));
  await assertSucceeds(logsRef.get());
  await assertSucceeds(
    logsRef.doc("log_1").update({
      resolved: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unauthenticated and cross-user reads/writes fail
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_mirage").collection("diagnosisLogs").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_mirage").collection("diagnosisLogs").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_mirage").collection("diagnosisLogs").doc("log_1").update({
      resolved: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Immutable / tampered fields rejected
  await assertFails(
    logsRef.doc("log_1").update({
      userPrompt: "tampered prompt",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    logsRef.doc("log_bad").set({
      ...validLog,
      attachmentType: "UNKNOWN_TYPE",
    })
  );

  await assertSucceeds(logsRef.doc("log_1").delete());
});

test("ObdSessions: owner create/list/delete succeed while unauthenticated, cross-user, ghost, or unverified writes fail", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceVehicleRef = aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_gtr");
  await assertSucceeds(aliceVehicleRef.set(validVehiclePayload(ALICE_UID)));

  const sessionsRef = aliceVehicleRef.collection("obdSessions");
  const validSession = {
    userId: ALICE_UID,
    vehicleId: "veh_gtr",
    vehicleTitle: "2024 Nissan GT-R",
    sourceType: "MANUAL_DTC_ENTRY",
    adapterOrFileLabel: "Owner Manual Entry",
    reliabilityLevel: "LOW_MANUAL_OR_UNVERIFIED",
    overallUrgency: "Service Recommended",
    confidenceLevel: "Low",
    ownerSymptoms: "Rough idle after cold start",
    headlineSummary: "Engine computer logged P0171 indicating a lean air-fuel balance.",
    driveabilityNotice: "An OBD scan alone cannot guarantee full mechanical safety.",
    dtcCodes: ["P0171"],
    findingTitles: ["P0171 — Fuel & Air Mix Too Lean"],
    telemetrySummary: ["PID 0105 Coolant Temp: Unavailable"],
    missingInformation: ["Live Short/Long Term Fuel Trim readings"],
    mechanicChecklist: ["Inspect intake boot and perform vacuum smoke check"],
    reportPlainText: "BRANDT Diagnostic Summary: P0171 on 2024 Nissan GT-R",
    readOnlyVerified: true,
    createdAt: now,
    updatedAt: now,
  };

  // Owner create, list, get, delete succeed
  await assertSucceeds(sessionsRef.doc("sess_1").set(validSession));
  await assertSucceeds(sessionsRef.get());
  await assertSucceeds(sessionsRef.doc("sess_1").get());

  // Unauthenticated and cross-user reads/writes/deletes fail
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_gtr").collection("obdSessions").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_gtr").collection("obdSessions").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_gtr").collection("obdSessions").doc("sess_bad_owner").set(validSession)
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_gtr").collection("obdSessions").doc("sess_1").delete()
  );

  // Reject ghost field, readOnlyVerified == false, invalid urgency enum, and updates
  await assertFails(
    sessionsRef.doc("sess_ghost").set({ ...validSession, extraField: "bad" })
  );
  await assertFails(
    sessionsRef.doc("sess_not_ro").set({ ...validSession, readOnlyVerified: false })
  );
  await assertFails(
    sessionsRef.doc("sess_bad_urgency").set({ ...validSession, overallUrgency: "Safe Green" })
  );
  await assertFails(
    sessionsRef.doc("sess_1").update({ overallUrgency: "Monitor", updatedAt: new Date(Date.now() - 500) })
  );

  await assertSucceeds(sessionsRef.doc("sess_1").delete());
});
