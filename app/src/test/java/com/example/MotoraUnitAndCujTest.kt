package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.CarChatReply
import com.example.data.ai.CarChatTurn
import com.example.data.ai.PendingChatAttachment
import com.example.data.ai.VehicleMechanicAiService
import com.example.data.ai.VehicleModelIssuesResult
import com.example.data.ai.VehicleVisionAnalyzer
import com.example.data.ai.VehicleVisionResult
import com.example.data.catalog.DemoVehicleCatalog
import com.example.data.model.AttachmentKind
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.RequestedCaptureType
import com.example.data.model.SpecSource
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleKnownIssue
import com.example.data.model.VehicleValidator
import com.example.data.model.VerificationStatus
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.ui.auth.AuthScreen
import com.example.data.obd.BluetoothAdapterUiState
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.BluetoothDeviceEntry
import com.example.data.obd.BluetoothHardwarePort
import com.example.data.obd.BluetoothObdAdapter
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdProtocolPreference
import com.example.data.obd.WifiHardwarePort
import com.example.data.obd.WifiObdAdapter
import com.example.ui.chat.VehicleAiChatScreen
import com.example.ui.chat.VehicleAiChatViewModel
import com.example.ui.diagnostics.DiagnosticsScreen
import com.example.ui.diagnostics.DiagnosticsViewModel
import com.example.ui.diagnostics.connect.DiagnosticsConnectScreen
import com.example.ui.diagnostics.connect.DiagnosticsConnectViewModel
import com.example.ui.diagnostics.explain.ObdExplainScreen
import com.example.ui.diagnostics.history.DiagnosticsHistoryScreen
import com.example.ui.diagnostics.report.DiagnosticsReportScreen
import com.example.ui.diagnostics.results.DiagnosticsResultsScreen
import com.example.ui.diagnostics.scan.DiagnosticsScanScreen
import com.example.ui.garage.GarageScreen
import com.example.ui.garage.GarageUiState
import com.example.ui.garage.GarageViewModel
import com.example.ui.log.IssueLogFilter
import com.example.ui.log.VehicleIssueLogScreen
import com.example.ui.log.VehicleIssueLogUiState
import com.example.ui.log.VehicleIssueLogViewModel
import com.example.ui.overview.VehicleKnownIssuesUiState
import com.example.ui.overview.VehicleOverviewScreen
import com.example.ui.overview.VehicleOverviewUiState
import com.example.ui.overview.VehicleOverviewViewModel
import com.example.ui.registration.PhotoCaptureGuideScreen
import com.example.ui.registration.VehicleFormScreen
import com.example.ui.registration.VehicleFormViewModel
import com.example.ui.studio.StudioAnalysisState
import com.example.ui.studio.VehicleStudioCaptureScreen
import com.example.ui.studio.VehicleStudioViewModel
import com.example.ui.components.BundledPcPhoto
import com.example.ui.components.autoMapBundledPhotosToAngles
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.ui.theme.MotoraTheme
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotoraUnitAndCujTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetLanguageToEnglishDefault() {
        LanguagePreferenceStore.setLanguageImmediate(AppLanguage.ENGLISH)
    }

    private suspend fun <T> StateFlow<T>.awaitMainState(
        timeoutMs: Long = 5_000L,
        predicate: (T) -> Boolean
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val current = value
            if (predicate(current)) return current
            delay(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val finalState = value
        assertTrue("Timed out after ${timeoutMs}ms waiting for StateFlow predicate; last=$finalState", predicate(finalState))
        return finalState
    }

    // --- 1. VALIDATION & CATALOG LOGIC TESTS ---

    @Test
    fun `validator rejects missing required fields, invalid model year, and negative mileage`() {
        val emptyDraft = VehicleDraft()
        val result = VehicleValidator.validate(emptyDraft)
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_MANUFACTURER))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_MODEL))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_MODEL_YEAR))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_TRIM))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_ENGINE_TYPE))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_TRANSMISSION))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_FUEL_TYPE))
        assertTrue(result.fieldErrors.containsKey(VehicleValidator.FIELD_MILEAGE))

        val badYearAndMileage = VehicleDraft(
            manufacturer = "Volvo",
            model = "EX30",
            modelYearInput = "1800",
            trim = "Core",
            engineType = "BEV",
            transmission = "Direct Drive",
            fuelType = "Electric",
            mileageInput = "-5",
            market = "Sweden / EU"
        )
        val badResult = VehicleValidator.validate(badYearAndMileage)
        assertFalse(badResult.isValid)
        assertTrue(badResult.fieldErrors.containsKey(VehicleValidator.FIELD_MODEL_YEAR))
        assertTrue(badResult.fieldErrors.containsKey(VehicleValidator.FIELD_MILEAGE))
    }

    @Test
    fun `validator supports unknown specifications and optional displacement`() {
        val draftWithUnknowns = VehicleDraft(
            manufacturer = "Mazda",
            model = "RX-7",
            modelYearInput = "1994",
            trim = "Touring",
            engineType = "",
            engineDisplacement = "",
            transmission = "",
            fuelType = "Petrol / Gasoline",
            mileageInput = "84500",
            mileageUnit = MileageUnit.MI,
            market = "Japan",
            unknownFields = setOf(
                VehicleValidator.FIELD_ENGINE_TYPE,
                VehicleValidator.FIELD_TRANSMISSION
            )
        )
        val result = VehicleValidator.validate(draftWithUnknowns)
        assertTrue(result.isValid)
        assertEquals(1994, result.parsedYear)
        assertEquals(84500L, result.parsedMileage)
    }

    @Test
    fun `dependent demonstration catalog resolves models and variants and marks demo provenance`() {
        assertTrue(DemoVehicleCatalog.IS_DEMONSTRATION_CATALOG)
        val volvoModels = DemoVehicleCatalog.modelsForManufacturer("Volvo")
        assertTrue(volvoModels.any { it.name == "EX30" })
        val ex30Variants = DemoVehicleCatalog.variantsForModel("Volvo", "EX30")
        assertTrue(ex30Variants.any { it.trimName == "Twin Motor Performance" })
        assertTrue(ex30Variants.all { it.isDemonstrationData })
    }

    // --- 2. REPOSITORY PERSISTENCE, RELOAD & MULTI-USER ACCESS CONTROL ---

    @Test
    fun `create, edit, primary switch, reload persistence, delete, and cross-user isolation work`() {
        runBlocking {
            val store = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_alice")

            // Add 2 vehicles for Alice
            val ex30Id = store.createVehicle(
                "owner_alice",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Twin Motor Performance",
                    bodyStyle = BodyStyle.CROSSOVER,
                    engineType = "Dual-Motor AWD Electric (BEV)",
                    engineDisplacement = "N/A (Electric)",
                    transmission = "Single-Speed Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "1200",
                    mileageUnit = MileageUnit.KM,
                    market = "Sweden / EU",
                    specSource = SpecSource.DEMO_CATALOG_PREFILL
                )
            ).getOrThrow()

            val porscheId = store.createVehicle(
                "owner_alice",
                VehicleDraft(
                    manufacturer = "Porsche",
                    model = "911",
                    modelYearInput = "2023",
                    trim = "Carrera GTS",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "Twin-Turbo Flat-6",
                    engineDisplacement = "2981 cc",
                    transmission = "8-Speed PDK Dual-Clutch",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "9400",
                    mileageUnit = MileageUnit.MI,
                    market = "United States",
                    setAsPrimary = false,
                    specSource = SpecSource.USER_ENTERED
                )
            ).getOrThrow()

            // First vehicle is automatically primary; second is non-primary
            var aliceVehicles = store.observeVehicles("owner_alice").first()
            assertEquals(2, aliceVehicles.size)
            assertTrue(aliceVehicles.first { it.id == ex30Id }.primary)
            assertFalse(aliceVehicles.first { it.id == porscheId }.primary)

            // Switch primary vehicle to Porsche 911
            store.setPrimaryVehicle("owner_alice", porscheId).getOrThrow()
            aliceVehicles = store.observeVehicles("owner_alice").first()
            assertFalse(aliceVehicles.first { it.id == ex30Id }.primary)
            assertTrue(aliceVehicles.first { it.id == porscheId }.primary)

            // Edit EX30 with unknown displacement and verify provenance & coverage
            val ex30Existing = aliceVehicles.first { it.id == ex30Id }
            store.updateVehicle(
                "owner_alice",
                ex30Existing,
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Single Motor Extended Range",
                    bodyStyle = BodyStyle.CROSSOVER,
                    engineType = "Rear Electric (BEV)",
                    engineDisplacement = "",
                    transmission = "Single-Speed Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "2450",
                    mileageUnit = MileageUnit.KM,
                    market = "Sweden / EU",
                    setAsPrimary = false,
                    specSource = SpecSource.USER_ENTERED,
                    unknownFields = setOf(VehicleValidator.FIELD_ENGINE_DISPLACEMENT)
                )
            ).getOrThrow()

            // Simulate app reload by instantiating a fresh ViewModel over the same persisted store
            val reloadedGarageVm = GarageViewModel(store, "owner_alice", "Alice")
            val reloadedState = reloadedGarageVm.uiState.first { it is GarageUiState.Ready } as GarageUiState.Ready
            val reloadedEx30 = reloadedState.vehicles.first { it.id == ex30Id }
            assertEquals("Single Motor Extended Range", reloadedEx30.trim)
            assertEquals(2450L, reloadedEx30.mileage)
            assertEquals(UNKNOWN_SPEC_LABEL, reloadedEx30.engineDisplacement)
            assertEquals(VerificationStatus.UNVERIFIED_USER_DATA, reloadedEx30.resolvedVerificationStatus())

            // Cross-user access control: Switch active user to Bob and assert Alice's vehicles are inaccessible
            store.activeAuthenticatedUserId = "owner_bob"
            val crossUserDelete = store.deleteVehicle("owner_alice", ex30Id)
            assertTrue(crossUserDelete.isFailure)

            val bobVehicles = store.observeVehicles("owner_bob").first()
            assertEquals(0, bobVehicles.size)

            // Switch back to Alice, delete primary (Porsche), verify EX30 automatically promoted to primary
            store.activeAuthenticatedUserId = "owner_alice"
            store.deleteVehicle("owner_alice", porscheId).getOrThrow()
            val remaining = store.observeVehicles("owner_alice").first()
            assertEquals(1, remaining.size)
            assertEquals(ex30Id, remaining.first().id)
            assertTrue(remaining.first().primary)
        }
    }

    // --- 3. COMPOSE UI CUJ TESTS (GARAGE, REGISTRATION, OVERVIEW, RESPONSIVE LAYOUTS) ---

    @Test
    fun `compose registration flow validates, creates vehicle, and renders overview with honest future modules`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_1")
        val formVm = VehicleFormViewModel(store, "user_1", editingVehicleId = null)
        var savedId: String? = null

        composeRule.setContent {
            MotoraTheme {
                VehicleFormScreen(
                    viewModel = formVm,
                    onNavigateBack = {},
                    onVehicleSaved = { savedId = it }
                )
            }
        }

        // Switch to Custom Entry and test validation error on empty submit
        composeRule.onNodeWithTag("custom_entry_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("submit_vehicle_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("form_error_banner").performScrollTo().assertIsDisplayed()

        // Fill valid custom vehicle with Unknown transmission
        composeRule.onNodeWithTag("manufacturer_input").performScrollTo().performTextInput("Polestar")
        composeRule.onNodeWithTag("model_input").performScrollTo().performTextInput("Polestar 2")
        composeRule.onNodeWithTag("trim_input").performScrollTo().performTextInput("Long Range Dual Motor")
        composeRule.onNodeWithTag("model_year_input").performScrollTo().performTextInput("2024")
        composeRule.onNodeWithTag("engine_type_input").performScrollTo().performTextInput("Dual-Motor BEV")
        composeRule.onNodeWithTag("transmission_unknown_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("fuel_type_input").performScrollTo().performTextInput("Electric")
        composeRule.onNodeWithTag("mileage_input").performScrollTo().performTextInput("6100")

        composeRule.onNodeWithTag("submit_vehicle_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedId)
        val created = runBlocking { store.observeVehicles("user_1").first() }.first()
        assertEquals("Polestar", created.manufacturer)
        assertEquals(UNKNOWN_SPEC_LABEL, created.transmission)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compose overview screen displays identity, coverage, mileage update, and planned modules`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_1")
        val id = runBlocking {
            store.createVehicle(
                "user_1",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Twin Motor Performance",
                    bodyStyle = BodyStyle.CROSSOVER,
                    engineType = "Dual-Motor Electric (BEV)",
                    engineDisplacement = "N/A (Electric)",
                    transmission = "Single-Speed Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "3200",
                    mileageUnit = MileageUnit.KM,
                    market = "Sweden / EU",
                    specSource = SpecSource.DEMO_CATALOG_PREFILL
                )
            ).getOrThrow()
        }

        val overviewVm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_1",
            vehicleId = id,
            autoLoadKnownIssues = false
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = overviewVm,
                    onNavigateBack = {},
                    onEditVehicle = {}
                )
            }
        }

        runBlocking { overviewVm.uiState.first { it is VehicleOverviewUiState.Ready } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overview_vehicle_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Volvo EX30").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overview_mileage_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overview_live_vehicle_data_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_driving_engine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_engine_temp").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_air_intake_fuel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_transmission").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_diagnostic_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overview_vehicle_insight_placeholder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("verification_coverage_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overview_registration_details_card").performScrollTo().assertIsDisplayed()

        // Ensure AI chat buttons are removed from Car Overview as requested
        assertTrue(composeRule.onAllNodesWithTag("overview_open_ai_chat_button").fetchSemanticsNodes().isEmpty())

        // Quick mileage update via inline editor
        composeRule.onNodeWithTag("update_mileage_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overview_mileage_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("overview_mileage_input").performScrollTo().performTextInput("4500")
        composeRule.onNodeWithTag("save_mileage_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        val updated = runBlocking { store.observeVehicle("user_1", id).first() }!!
        assertEquals(4500L, updated.mileage)
    }

    @Test
    @Config(qualifiers = "w960dp-h900dp")
    fun `compose garage supports delete confirmation dialog and responsive compact vs expanded layouts`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_1")
        val id1 = runBlocking {
            val firstId = store.createVehicle(
                "user_1",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Core",
                    engineType = "Electric",
                    transmission = "Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "500",
                    market = "Sweden / EU"
                )
            ).getOrThrow()
            store.createVehicle(
                "user_1",
                VehicleDraft(
                    manufacturer = "BMW",
                    model = "i4",
                    modelYearInput = "2024",
                    trim = "eDrive40",
                    engineType = "Electric",
                    transmission = "Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "8900",
                    market = "Germany"
                )
            ).getOrThrow()
            firstId
        }

        val garageVm = GarageViewModel(store, "user_1", "Alex")
        runBlocking { garageVm.uiState.first { it is GarageUiState.Ready } }

        // Test Expanded (Tablet) Layout at 960dp width
        composeRule.setContent {
            MotoraTheme {
                GarageScreen(
                    viewModel = garageVm,
                    ownerDisplayName = "Alex",
                    onAddVehicle = {},
                    onOpenVehicleOverview = {},
                    onEditVehicle = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("garage_expanded_layout").assertIsDisplayed()

        // Trigger delete confirmation dialog for id1 and confirm removal
        composeRule.onNodeWithTag("delete_vehicle_$id1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_button").performClick()
        composeRule.waitForIdle()

        val afterDelete = runBlocking { store.observeVehicles("user_1").first() }
        assertEquals(1, afterDelete.size)
        assertEquals("BMW", afterDelete.first().manufacturer)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compose garage renders compact layout on phone width`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_1")
        runBlocking {
            store.createVehicle(
                "user_1",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Core",
                    engineType = "Electric",
                    transmission = "Direct Drive",
                    fuelType = "Electric",
                    mileageInput = "500",
                    market = "Sweden / EU"
                )
            ).getOrThrow()
        }

        val garageVm = GarageViewModel(store, "user_1", "Alex")
        runBlocking { garageVm.uiState.first { it is GarageUiState.Ready } }

        composeRule.setContent {
            MotoraTheme {
                GarageScreen(
                    viewModel = garageVm,
                    ownerDisplayName = "Alex",
                    onAddVehicle = {},
                    onOpenVehicleOverview = {},
                    onEditVehicle = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("garage_compact_list").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `empty garage screen renders BRANDT editorial headline, top-down coupe cutout, and mint Add Your First Car CTA`() {
        val emptyStore = InMemoryPreviewVehicleDataSource()
        val garageVm = GarageViewModel(emptyStore, "preview_empty_user", "Alex")
        runBlocking { garageVm.uiState.first { it is GarageUiState.Ready } }

        var addVehicleClicked = false
        composeRule.setContent {
            MotoraTheme {
                GarageScreen(
                    viewModel = garageVm,
                    ownerDisplayName = "Alex",
                    onAddVehicle = { addVehicleClicked = true },
                    onOpenVehicleOverview = {},
                    onEditVehicle = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("empty_garage_headline").assertIsDisplayed()
        composeRule.onNodeWithText("Add Your First Car").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_garage_car_image").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_add_vehicle_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(addVehicleClicked)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `guided photo capture intermediate screen renders 4 angle cards, why-photo privacy explanation, and advances on Lets Photo`() {
        var backClicked = false
        var continueClicked = false

        composeRule.setContent {
            MotoraTheme {
                PhotoCaptureGuideScreen(
                    onNavigateBack = { backClicked = true },
                    onContinueToPhotoCapture = { continueClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("photo_guide_title").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_card_front").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_card_left").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_card_right").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_card_rear").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_why_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_privacy_note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("guide_lets_photo_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(continueClicked)

        composeRule.onNodeWithTag("guide_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue(backClicked)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `preview mode supports entering garage without google sign-in and managing in-memory vehicles`() {
        val previewStore = InMemoryPreviewVehicleDataSource()
        runBlocking {
            val id = previewStore.createVehicle(
                "preview_local_session",
                VehicleDraft(
                    manufacturer = "Porsche",
                    model = "Taycan",
                    modelYearInput = "2025",
                    trim = "4S",
                    engineType = "Dual-Motor Electric",
                    transmission = "2-Speed Automatic",
                    fuelType = "Electric",
                    mileageInput = "120",
                    market = "Germany"
                )
            ).getOrThrow()
            assertTrue(id.startsWith("preview_"))
            val vehicles = previewStore.observeVehicles("preview_local_session").first()
            assertEquals(1, vehicles.size)
            assertTrue(vehicles.first().primary)
        }

        val garageVm = GarageViewModel(previewStore, "preview_local_session", "Preview Session")
        runBlocking { garageVm.uiState.first { it is GarageUiState.Ready } }

        composeRule.setContent {
            MotoraTheme {
                var inPreview by remember { mutableStateOf(false) }
                if (!inPreview) {
                    AuthScreen(onEnterPreviewMode = { inPreview = true })
                } else {
                    GarageScreen(
                        viewModel = garageVm,
                        ownerDisplayName = "Preview Session",
                        isPreviewMode = true,
                        onAddVehicle = {},
                        onOpenVehicleOverview = {},
                        onEditVehicle = {},
                        onSignOut = { inPreview = false }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("brandt_hero_wordmark").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_mode_title").assertTextEquals("Create Account")
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("facebook_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_name_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().assertIsDisplayed()

        // Toggle Create Account -> Sign in Account mode (hides Name field, keeps Email & Password)
        composeRule.onNodeWithTag("auth_toggle_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_mode_title").assertTextEquals("Sign in Account")
        composeRule.onNodeWithTag("auth_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().assertIsDisplayed()

        // Enter Preview Mode and verify transition to Garage & return back
        composeRule.onNodeWithTag("preview_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("preview_mode_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("garage_compact_list").assertIsDisplayed()

        composeRule.onNodeWithTag("exit_preview_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview_mode_button").performScrollTo().assertIsDisplayed()
    }

    // --- 4. OPTION C HYBRID: 360 MULTI-ANGLE STUDIO & GEMINI VISION TESTS ---

    @Test
    fun `studio viewmodel advances angles, runs vision analysis, propagates errors, and saves to existing vehicle`() {
        runBlocking {
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val store = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_studio")
            val vehicleId = store.createVehicle(
                "owner_studio",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Core",
                    engineType = "BEV",
                    transmission = "Single-Speed",
                    fuelType = "Electric",
                    mileageInput = "300",
                    market = "Sweden / EU"
                )
            ).getOrThrow()

            var shouldFail = false
            val fakeAnalyzer = object : VehicleVisionAnalyzer {
                override suspend fun analyzeVehicleAngles(
                    context: Context,
                    anglePhotos: Map<VehicleAngle, String>
                ): Result<VehicleVisionResult> {
                    return if (shouldFail) {
                        Result.failure(IllegalStateException("Network timeout calling Gemini Vision"))
                    } else {
                        Result.success(
                            VehicleVisionResult(
                                manufacturer = "Volvo",
                                model = "EX30",
                                modelYearGuess = "2025",
                                bodyStyle = BodyStyle.CROSSOVER,
                                fuelTypeHint = "Electric",
                                trimOrBadgeHints = "Twin Motor",
                                exteriorSummary = "Silver compact electric crossover with Thor's hammer LED signature.",
                                angleCoverageOk = true,
                                qualityFeedback = "Clear front and side framing."
                            )
                        )
                    }
                }
            }

            val studioVm = VehicleStudioViewModel(
                repository = store,
                currentUserId = "owner_studio",
                targetVehicleId = vehicleId,
                visionAnalyzer = fakeAnalyzer
            )

            // Assign angles sequentially and verify auto-advance FRONT -> SIDE_LEFT -> REAR -> SIDE_RIGHT
            assertEquals(VehicleAngle.FRONT, studioVm.uiState.value.activeAngle)
            studioVm.assignPhotoToActiveAngle("file:///tmp/front.jpg")
            assertEquals(VehicleAngle.SIDE_LEFT, studioVm.uiState.value.activeAngle)
            studioVm.assignPhotoToActiveAngle("file:///tmp/left.jpg")
            assertEquals(VehicleAngle.REAR, studioVm.uiState.value.activeAngle)
            studioVm.clearAngle(VehicleAngle.SIDE_LEFT)
            assertFalse(studioVm.uiState.value.anglePhotos.containsKey(VehicleAngle.SIDE_LEFT))
            studioVm.assignPhotoToAngle(VehicleAngle.REAR, "file:///tmp/rear.jpg")

            // Verify failure propagates honestly (no silent mock fallback)
            shouldFail = true
            studioVm.analyzeWithGeminiVision(appContext)
            val errState = studioVm.uiState.awaitMainState { it.analysisState is StudioAnalysisState.Error }
            assertTrue((errState.analysisState as StudioAnalysisState.Error).message.contains("Network timeout"))

            // Verify success updates analysis state & persists into vehicle record
            shouldFail = false
            studioVm.analyzeWithGeminiVision(appContext)
            val okState = studioVm.uiState.awaitMainState { it.analysisState is StudioAnalysisState.Success }
            assertEquals("Volvo", (okState.analysisState as StudioAnalysisState.Success).result.manufacturer)

            studioVm.saveStudioToExistingVehicle()
            studioVm.uiState.awaitMainState { it.savedSuccessfully }

            val updated = store.observeVehicle("owner_studio", vehicleId).first()!!
            assertEquals(2, updated.angleCoverageCount())
            assertTrue(updated.aiInspectionSummary.contains("Silver compact electric crossover"))
        }
    }

    @Test
    fun `form viewmodel applies AI vision studio capture with AI_EXTRACTED_UNVERIFIED provenance`() {
        runBlocking {
            val previewStore = InMemoryPreviewVehicleDataSource()
            val formVm = VehicleFormViewModel(previewStore, "preview_local_session", editingVehicleId = null)

            val capturedAngles = mapOf(
                VehicleAngle.FRONT to "file:///tmp/front_ex30.jpg",
                VehicleAngle.SIDE_LEFT to "file:///tmp/left_ex30.jpg",
                VehicleAngle.REAR to "file:///tmp/rear_ex30.jpg"
            )
            val vision = VehicleVisionResult(
                manufacturer = "Porsche",
                model = "Taycan",
                modelYearGuess = "2024",
                bodyStyle = BodyStyle.SEDAN,
                fuelTypeHint = "Electric",
                trimOrBadgeHints = "4S",
                exteriorSummary = "White four-door electric sports saloon.",
                angleCoverageOk = true,
                qualityFeedback = "Right side angle still empty."
            )

            formVm.applyStudioCapture(capturedAngles, vision)
            val draft = formVm.uiState.value.draft
            assertEquals("Porsche", draft.manufacturer)
            assertEquals("Taycan", draft.model)
            assertEquals("2024", draft.modelYearInput)
            assertEquals(BodyStyle.SEDAN, draft.bodyStyle)
            assertEquals(SpecSource.AI_VISION_PREFILL, draft.specSource)
            assertEquals(3, draft.anglePhotos.size)

            formVm.updateEngineType("Dual-Motor Permanent Magnet")
            formVm.updateTransmission("2-Speed Rear / Direct Front")
            formVm.updateMileage("1500")
            formVm.submit()

            val createdId = formVm.uiState.awaitMainState { it.savedVehicleId != null }.savedVehicleId!!
            val saved = previewStore.observeVehicle("preview_local_session", createdId).first()!!
            assertEquals(VerificationStatus.AI_EXTRACTED_UNVERIFIED, saved.resolvedVerificationStatus())
            assertEquals(SpecSource.AI_VISION_PREFILL, saved.resolvedSpecSource())
            assertEquals(3, saved.angleCoverageCount())
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compose overview renders AI observation summary and planned intelligence modules without 3D studio`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_ai_overview")
        val id = runBlocking {
            store.createVehicle(
                "user_ai_overview",
                VehicleDraft(
                    manufacturer = "Mitsubishi",
                    model = "Mirage",
                    modelYearInput = "2020",
                    trim = "GLX",
                    bodyStyle = BodyStyle.HATCHBACK,
                    engineType = "1.2L 3-Cylinder MIVEC",
                    transmission = "CVT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "42500",
                    market = "Thailand",
                    photoUri = "file:///tmp/front.jpg",
                    aiInspectionSummary = "White 5-door Mirage hatchback with Mitsubishi emblem on front grille.",
                    specSource = SpecSource.AI_VISION_PREFILL
                )
            ).getOrThrow()
        }

        val overviewVm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_ai_overview",
            vehicleId = id,
            autoLoadKnownIssues = false
        )
        runBlocking { overviewVm.uiState.first { it is VehicleOverviewUiState.Ready } }

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = overviewVm,
                    onNavigateBack = {},
                    onEditVehicle = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overview_vehicle_title").assertIsDisplayed()
        composeRule.onNodeWithText("Mitsubishi Mirage").assertIsDisplayed()
        composeRule.onNodeWithTag("overview_ai_summary_box").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overview_registration_details_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compose studio screen renders angle selector and gemini vision analysis result`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_cam")
        val fakeAnalyzer = object : VehicleVisionAnalyzer {
            override suspend fun analyzeVehicleAngles(
                context: Context,
                anglePhotos: Map<VehicleAngle, String>
            ): Result<VehicleVisionResult> = Result.success(
                VehicleVisionResult(
                    manufacturer = "BMW",
                    model = "i4",
                    modelYearGuess = "2024",
                    bodyStyle = BodyStyle.SEDAN,
                    fuelTypeHint = "Electric",
                    trimOrBadgeHints = "M50",
                    exteriorSummary = "Frozen blue fastback electric gran coupe.",
                    angleCoverageOk = true,
                    qualityFeedback = "Add rear shot for badge confirmation."
                )
            )
        }
        val studioVm = VehicleStudioViewModel(
            repository = store,
            currentUserId = "user_cam",
            targetVehicleId = null,
            initialAnglePhotos = mapOf(VehicleAngle.FRONT to "file:///tmp/bmw_front.jpg"),
            visionAnalyzer = fakeAnalyzer
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleStudioCaptureScreen(
                    viewModel = studioVm,
                    isAttachedToExistingVehicle = false,
                    onNavigateBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("capture_angle_chip_SIDE_LEFT").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Left Profile (90°)").assertIsDisplayed()

        composeRule.onNodeWithTag("studio_browse_files_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("studio_pc_folder_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_pc_folder_panel").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("pc_folder_auto_fill_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("pc_folder_auto_fill_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("pc_folder_auto_fill_button").performClick()
        composeRule.waitForIdle()
        assertEquals(4, studioVm.uiState.value.anglePhotos.size)

        composeRule.onNodeWithTag("studio_toggle_url_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_url_panel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("studio_url_import_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_url_error").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("analyze_vision_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vision_result_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vision_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI VISION PRE-FILL (UNVERIFIED)").assertIsDisplayed()
    }

    @Test
    fun `pc folder auto mapper matches directional filenames cleanly`() {
        val mapped = autoMapBundledPhotosToAngles(
            listOf(
                BundledPcPhoto("car_rear_01.jpg", "file:///tmp/rear.jpg"),
                BundledPcPhoto("my_car_front.png", "file:///tmp/front.png"),
                BundledPcPhoto("side_right_view.webp", "file:///tmp/right.webp"),
                BundledPcPhoto("extra_shot.jpg", "file:///tmp/extra.jpg")
            )
        )
        assertEquals("file:///tmp/front.png", mapped[VehicleAngle.FRONT])
        assertEquals("file:///tmp/rear.jpg", mapped[VehicleAngle.REAR])
        assertEquals("file:///tmp/right.webp", mapped[VehicleAngle.SIDE_RIGHT])
        assertEquals("file:///tmp/extra.jpg", mapped[VehicleAngle.SIDE_LEFT])
    }

    @Test
    fun `form viewmodel attachPhotoAndAnalyze prefills vehicle fields or surfaces error`() {
        runBlocking {
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val store = InMemoryPreviewVehicleDataSource()
            var shouldFail = false
            val fakeAnalyzer = object : VehicleVisionAnalyzer {
                override suspend fun analyzeVehicleAngles(
                    context: Context,
                    anglePhotos: Map<VehicleAngle, String>
                ): Result<VehicleVisionResult> {
                    return if (shouldFail) {
                        Result.failure(IllegalStateException("Vision quota exceeded"))
                    } else {
                        Result.success(
                            VehicleVisionResult(
                                manufacturer = "Mitsubishi",
                                model = "Mirage",
                                modelYearGuess = "2020",
                                bodyStyle = BodyStyle.HATCHBACK,
                                fuelTypeHint = "Petrol / Gasoline",
                                trimOrBadgeHints = "GLX",
                                exteriorSummary = "White 5-door Mirage hatchback with multi-spoke alloy wheels.",
                                angleCoverageOk = false,
                                qualityFeedback = "Captured front angle."
                            )
                        )
                    }
                }
            }

            val formVm = VehicleFormViewModel(
                repository = store,
                currentUserId = "preview_local_session",
                editingVehicleId = null,
                visionAnalyzer = fakeAnalyzer
            )

            // Failure path surfaces honest inline error
            shouldFail = true
            formVm.attachPhotoAndAnalyze(appContext, "file:///tmp/mirage_front.jpg")
            val errState = formVm.uiState.awaitMainState { !it.isAnalyzingPhoto && it.aiAnalysisError != null }
            assertTrue(errState.aiAnalysisError!!.contains("Vision quota exceeded"))

            // Success path populates fields right inside Register Vehicle
            shouldFail = false
            formVm.analyzeAttachedPhotosWithAi(appContext)
            val filledState = formVm.uiState.awaitMainState {
                !it.isAnalyzingPhoto && it.draft.manufacturer == "Mitsubishi"
            }
            assertEquals("Mirage", filledState.draft.model)
            assertEquals("2020", filledState.draft.modelYearInput)
            assertEquals(BodyStyle.HATCHBACK, filledState.draft.bodyStyle)
            assertEquals("Petrol / Gasoline", filledState.draft.fuelType)
            assertEquals("GLX", filledState.draft.trim)
            assertEquals(SpecSource.AI_VISION_PREFILL, filledState.draft.specSource)
            assertTrue(filledState.useCustomEntry)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `compose register vehicle form supports inline AI photo auto-fill and error banner`() {
        val store = InMemoryPreviewVehicleDataSource()
        var shouldFail = true
        val fakeAnalyzer = object : VehicleVisionAnalyzer {
            override suspend fun analyzeVehicleAngles(
                context: Context,
                anglePhotos: Map<VehicleAngle, String>
            ): Result<VehicleVisionResult> {
                return if (shouldFail) {
                    Result.failure(IllegalStateException("Gemini Vision temporary error"))
                } else {
                    Result.success(
                        VehicleVisionResult(
                            manufacturer = "Mitsubishi",
                            model = "Mirage",
                            modelYearGuess = "2020",
                            bodyStyle = BodyStyle.HATCHBACK,
                            fuelTypeHint = "Petrol / Gasoline",
                            trimOrBadgeHints = "Active",
                            exteriorSummary = "White compact hatchback with silver wheel covers.",
                            angleCoverageOk = true,
                            qualityFeedback = "All 4 angles present."
                        )
                    )
                }
            }
        }
        val formVm = VehicleFormViewModel(
            repository = store,
            currentUserId = "preview_local_session",
            editingVehicleId = null,
            visionAnalyzer = fakeAnalyzer
        )
        formVm.updatePhotoUri("file:///tmp/mirage_front.jpg")

        composeRule.setContent {
            MotoraTheme {
                VehicleFormScreen(
                    viewModel = formVm,
                    onNavigateBack = {},
                    onVehicleSaved = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("form_pc_folder_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("form_pc_folder_strip").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("form_pc_folder_strip").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("form_ai_analyze_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("form_ai_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("form_ai_error_banner").performScrollTo().assertIsDisplayed()

        shouldFail = false
        composeRule.onNodeWithTag("form_ai_analyze_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("form_ai_summary_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("form_ai_summary_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mitsubishi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mirage").performScrollTo().assertIsDisplayed()
    }

    // --- 5. AI CAR BUDDY CHAT (+ PHOTO / FILE / AUDIO) + KNOWN ISSUES + PROBLEM LOG TESTS ---

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `overview displays proactive known issues with error retry and tappable AI and Log icon shortcuts`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_issues")
        val id = runBlocking {
            store.createVehicle(
                "user_issues",
                VehicleDraft(
                    manufacturer = "Mitsubishi",
                    model = "Mirage",
                    modelYearInput = "2019",
                    trim = "GLX",
                    bodyStyle = BodyStyle.HATCHBACK,
                    engineType = "1.2L MIVEC",
                    transmission = "CVT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "68000",
                    market = "Thailand"
                )
            ).getOrThrow()
        }

        var shouldFailIssues = true
        val observedLanguages = mutableListOf<AppLanguage>()
        val fakeMechanicAi = object : VehicleMechanicAiService {
            override suspend fun loadCommonIssuesForVehicle(
                vehicle: Vehicle,
                language: AppLanguage
            ): Result<VehicleModelIssuesResult> {
                observedLanguages.add(language)
                return if (shouldFailIssues) {
                    Result.failure(IllegalStateException("Temporary AI timeout"))
                } else {
                    Result.success(
                        if (language == AppLanguage.THAI) {
                            VehicleModelIssuesResult(
                                headlineSummary = "จุดสำคัญสำหรับ Mirage ปี 2019 คือการบำรุงรักษาน้ำมันเกียร์ CVT และระบบช่วงล่าง",
                                issues = listOf(
                                    VehicleKnownIssue(
                                        title = "การตอบสนองของเกียร์ CVT",
                                        frequencyLabel = "พบบ้างตามอายุใช้งาน",
                                        simpleExplanation = "น้ำมันเกียร์ CVT ที่เสื่อมสภาพทำให้แรงดันไฮดรอลิกในชุดพูลเลย์ลดลง",
                                        whatToNotice = "รอบเครื่องขึ้นสูงแต่รถออกตัวหน่วง",
                                        askAiPrompt = "อาการหน่วงของเกียร์ CVT ควรตรวจเช็กอย่างไร"
                                    )
                                )
                            )
                        } else {
                            VehicleModelIssuesResult(
                                headlineSummary = "Key maintenance focus for a 2019 Mirage is CVT fluid condition and front suspension bushings.",
                                issues = listOf(
                                    VehicleKnownIssue(
                                        title = "CVT Acceleration Hesitation",
                                        frequencyLabel = "Age / mileage wear",
                                        simpleExplanation = "Degraded CVT transmission fluid reduces hydraulic clamping pressure on the pulleys, causing sluggish launch response.",
                                        whatToNotice = "Engine RPM rises higher than normal before speed builds smoothly.",
                                        askAiPrompt = "What causes CVT hesitation under light acceleration?"
                                    )
                                )
                            )
                        }
                    )
                }
            }

            override suspend fun sendCarMechanicMessage(
                context: Context,
                vehicle: Vehicle,
                history: List<CarChatTurn>,
                userText: String,
                attachment: PendingChatAttachment?,
                language: AppLanguage
            ): Result<CarChatReply> = error("unused")
        }

        val overviewVm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_issues",
            vehicleId = id,
            aiService = fakeMechanicAi,
            autoLoadKnownIssues = true
        )

        var openedAiTopic: String? = null
        var openedAiClicked = false
        var openedLogClicked = false

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = overviewVm,
                    onNavigateBack = {},
                    onEditVehicle = {},
                    onOpenAiChat = { _, topic ->
                        openedAiClicked = true
                        openedAiTopic = topic
                    },
                    onOpenIssueLog = { openedLogClicked = true }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            overviewVm.knownIssuesState.value is VehicleKnownIssuesUiState.Error
        }
        composeRule.onNodeWithTag("overview_known_issues_error").performScrollTo().assertIsDisplayed()

        // Retry succeeds and defaults to English UI + English AI response
        shouldFailIssues = false
        composeRule.onNodeWithTag("overview_retry_known_issues_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            overviewVm.knownIssuesState.value is VehicleKnownIssuesUiState.Ready
        }
        composeRule.onNodeWithText("Known Model Issues & Watch-Outs").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("known_issue_item_0").performScrollTo().assertIsDisplayed()
        // AI chat buttons must not appear on Car Overview screen
        assertTrue(composeRule.onAllNodesWithTag("known_issue_ask_ai_0").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("overview_open_ai_chat_button").fetchSemanticsNodes().isEmpty())
        assertEquals(AppLanguage.ENGLISH, observedLanguages.last())

        // User switches language in Settings from English -> Thai -> refreshes UI & known model issues in Thai
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("language_option_th").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_done_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            observedLanguages.lastOrNull() == AppLanguage.THAI &&
                overviewVm.knownIssuesState.value is VehicleKnownIssuesUiState.Ready
        }
        composeRule.onNodeWithText("จุดสังเกตและอาการประจำรุ่นรถ").performScrollTo().assertIsDisplayed()

        // Switch back to English and verify Overview Log shortcut still navigates cleanly
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_option_en").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overview_open_log_button").performScrollTo().performClick()
        assertTrue(openedLogClicked)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `ai car chat defaults to English pro tone, supports plus icons, car-only guard, and saves only car issues to log`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_chat")
        val id = runBlocking {
            store.createVehicle(
                "user_chat",
                VehicleDraft(
                    manufacturer = "Mitsubishi",
                    model = "Mirage",
                    modelYearInput = "2020",
                    trim = "GLX",
                    bodyStyle = BodyStyle.HATCHBACK,
                    engineType = "1.2L MIVEC",
                    transmission = "CVT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "52000",
                    market = "Thailand"
                )
            ).getOrThrow()
        }

        val chatLanguages = mutableListOf<AppLanguage>()
        val fakeMechanicAi = object : VehicleMechanicAiService {
            override suspend fun loadCommonIssuesForVehicle(
                vehicle: Vehicle,
                language: AppLanguage
            ): Result<VehicleModelIssuesResult> = error("unused")

            override suspend fun sendCarMechanicMessage(
                context: Context,
                vehicle: Vehicle,
                history: List<CarChatTurn>,
                userText: String,
                attachment: PendingChatAttachment?,
                language: AppLanguage
            ): Result<CarChatReply> {
                chatLanguages.add(language)
                return if (userText.contains("recipe") || userText.contains("omelette")) {
                    Result.success(
                        CarChatReply(
                            isCarRelated = false,
                            simpleReply = "BRANDT Auto Specialist handles automotive diagnostics and vehicle maintenance only.",
                            modelCommonality = "",
                            followUpQuestion = "",
                            suggestedQuickReplies = emptyList(),
                            logSummary = ""
                        )
                    )
                } else {
                    Result.success(
                        CarChatReply(
                            isCarRelated = true,
                            simpleReply = "That high-pitched squeal during braking is typically the brake pad wear indicator — a small metal tab engineered to contact the brake rotor when pad friction material drops to service limits. Safe to drive calmly to a workshop for brake pad inspection.",
                            modelCommonality = "Common wear item by mileage",
                            followUpQuestion = "Does the squeal come from the front wheels or rear wheels during light braking?",
                            suggestedQuickReplies = listOf("Front wheels", "Rear wheels"),
                            logSummary = "Brake squeal on light braking: likely brake pad wear indicator contacting rotor; schedule brake inspection."
                        )
                    )
                }
            }
        }

        val chatVm = VehicleAiChatViewModel(
            repository = store,
            currentUserId = "user_chat",
            vehicleId = id,
            aiService = fakeMechanicAi
        )

        var switchedToLog = false
        composeRule.setContent {
            MotoraTheme {
                VehicleAiChatScreen(
                    viewModel = chatVm,
                    onNavigateBack = {},
                    onOpenIssueLog = { switchedToLog = true }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            chatVm.uiState.value.vehicle != null
        }

        // Verify English default header & MACK landing hero (embedded iPhone 17 - 22 artwork)
        composeRule.onNodeWithText("BRANDT Auto Specialist").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_mack_landing_hero")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("MEET MACK")
        composeRule.onNodeWithText("Ask anything...").assertIsDisplayed()

        // 1. Tap '+' button beside chat input -> verifies Frame 6 vertical capsule (Camera, Mic, Document+)
        composeRule.onNodeWithTag("chat_plus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_attachment_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_photo_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_file_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_attach_audio_button").assertIsDisplayed()

        // 2. Attach simulated recorded car audio file & send -> AI responds in pro automotive English & saves AUDIO log
        chatVm.attachAudioFileForTest("/tmp/fake_car_brake.m4a", durationSeconds = 6)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_pending_attachment_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_message_input").performTextInput("High-pitched squeal when braking")
        composeRule.onNodeWithTag("chat_send_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            chatVm.uiState.value.messages.size == 2 && !chatVm.uiState.value.isSending
        }

        composeRule.onNodeWithTag("chat_ai_bubble_1").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_quick_reply_1_0").assertIsDisplayed()
        assertEquals(AppLanguage.ENGLISH, chatLanguages.first())

        var logsAfterCarChat = runBlocking { store.observeDiagnosisLogs("user_chat", id).first() }
        assertEquals(1, logsAfterCarChat.size)
        assertEquals(AttachmentKind.AUDIO, logsAfterCarChat.first().resolvedAttachmentKind())
        assertEquals("Common wear item by mileage", logsAfterCarChat.first().modelCommonality)

        // 3. Off-topic prompt -> politely refused AND NOT written into vehicle problem log
        composeRule.onNodeWithTag("chat_message_input").performTextInput("Give me an omelette recipe")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            chatVm.uiState.value.messages.size == 4 && !chatVm.uiState.value.isSending
        }
        composeRule.onNodeWithText("Automotive diagnostics only").assertIsDisplayed()

        logsAfterCarChat = runBlocking { store.observeDiagnosisLogs("user_chat", id).first() }
        assertEquals(1, logsAfterCarChat.size)

        // 4. Icon button in TopBar switches directly to separate Problem Log section
        composeRule.onNodeWithTag("chat_switch_to_log_button").performClick()
        assertTrue(switchedToLog)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `issue log section renders saved car problems, toggles resolved status, filters, deletes, and translates via Settings`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_log")
        val id = runBlocking {
            val vId = store.createVehicle(
                "user_log",
                VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Core",
                    engineType = "Electric",
                    transmission = "Single-Speed",
                    fuelType = "Electric",
                    mileageInput = "4100",
                    market = "Thailand"
                )
            ).getOrThrow()
            store.addDiagnosisLog(
                userId = "user_log",
                vehicleId = vId,
                userPrompt = "AC blowing warm air on highway",
                aiSummary = "Inspect A/C compressor clutch engagement, refrigerant pressure, and cabin air filter restriction.",
                attachmentKind = AttachmentKind.PHOTO,
                attachmentLabel = "dashboard.jpg",
                modelCommonality = "Preventive service item",
                followUpQuestion = "Does airflow volume from the vents feel normal?"
            ).getOrThrow()
            vId
        }

        val logVm = VehicleIssueLogViewModel(store, "user_log", id)
        var switchBackToAi = false

        composeRule.setContent {
            MotoraTheme {
                VehicleIssueLogScreen(
                    viewModel = logVm,
                    onNavigateBack = {},
                    onOpenAiChat = { switchBackToAi = true }
                )
            }
        }

        val readyState = runBlocking { logVm.uiState.first { it is VehicleIssueLogUiState.Ready } as VehicleIssueLogUiState.Ready }
        val logId = readyState.allLogs.first().id
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Vehicle Issue Log").assertIsDisplayed()
        composeRule.onNodeWithTag("log_card_$logId").assertIsDisplayed()
        composeRule.onNodeWithTag("log_toggle_resolved_$logId").performClick()
        composeRule.waitForIdle()

        // Switch filter to OPEN -> should show empty filter state because item is now resolved
        composeRule.onNodeWithTag("log_filter_${IssueLogFilter.OPEN.name}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log_empty_state").assertIsDisplayed()

        // Toggle Settings to Thai -> filter labels and TopBar title translate cleanly
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_option_th").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ประวัติ Log ปัญหารถ").assertIsDisplayed()

        // Switch filter to RESOLVED -> item reappears; delete clears it
        composeRule.onNodeWithTag("log_filter_${IssueLogFilter.RESOLVED.name}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log_card_$logId").assertIsDisplayed()
        composeRule.onNodeWithTag("log_delete_$logId").performClick()
        composeRule.waitForIdle()

        val remaining = runBlocking { store.observeDiagnosisLogs("user_log", id).first() }
        assertTrue(remaining.isEmpty())

        composeRule.onNodeWithTag("log_switch_to_ai_button").performClick()
        assertTrue(switchBackToAi)
    }

    @Test
    fun `vision parser extracts fenced json and normalizes unknown fields`() {
        val raw = """
            ```json
            {
              "manufacturer": "Mitsubishi",
              "model": "Mirage",
              "modelYearGuess": "2019",
              "bodyStyle": "HATCHBACK",
              "fuelTypeHint": "Petrol / Gasoline",
              "trimOrBadgeHints": "N/A",
              "exteriorSummary": "White 5-door compact hatchback with Mitsubishi three-diamond front emblem.",
              "angleCoverageOk": true,
              "qualityFeedback": ""
            }
            ```
        """.trimIndent()

        val parsed = com.example.data.ai.FirebaseVehicleVisionAnalyzer.parseVisionJson(raw)
        assertEquals("Mitsubishi", parsed.manufacturer)
        assertEquals("Mirage", parsed.model)
        assertEquals("2019", parsed.modelYearGuess)
        assertEquals(BodyStyle.HATCHBACK, parsed.bodyStyle)
        assertEquals("Petrol / Gasoline", parsed.fuelTypeHint)
        assertEquals(UNKNOWN_SPEC_LABEL, parsed.trimOrBadgeHints)
        assertTrue(parsed.angleCoverageOk)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `ai chat renders proactive capture requests, video tile in plus menu, and logs video attachment`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_proactive")
        val id = runBlocking {
            store.createVehicle(
                "user_proactive",
                VehicleDraft(
                    manufacturer = "Honda",
                    model = "Civic",
                    modelYearInput = "2022",
                    trim = "RS",
                    bodyStyle = BodyStyle.SEDAN,
                    engineType = "1.5L VTEC Turbo",
                    transmission = "CVT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "34000",
                    market = "Thailand"
                )
            ).getOrThrow()
        }

        val fakeAi = object : VehicleMechanicAiService {
            override suspend fun loadCommonIssuesForVehicle(
                vehicle: Vehicle,
                language: AppLanguage
            ): Result<VehicleModelIssuesResult> = error("unused")

            override suspend fun sendCarMechanicMessage(
                context: Context,
                vehicle: Vehicle,
                history: List<CarChatTurn>,
                userText: String,
                attachment: PendingChatAttachment?,
                language: AppLanguage
            ): Result<CarChatReply> {
                return if (attachment is PendingChatAttachment.VideoClip) {
                    Result.success(
                        CarChatReply(
                            isCarRelated = true,
                            simpleReply = "The serpentine belt tensioner shows slight oscillation under load, causing the chirping sound.",
                            modelCommonality = "Check accessory belt & tensioner",
                            followUpQuestion = "",
                            suggestedQuickReplies = emptyList(),
                            logSummary = "Engine bay video: serpentine belt tensioner oscillation causing belt chirp.",
                            requestedCaptureType = RequestedCaptureType.NONE,
                            requestedCaptureReason = ""
                        )
                    )
                } else {
                    Result.success(
                        CarChatReply(
                            isCarRelated = true,
                            simpleReply = "I need to inspect your engine accessory belt and hear the rattle live while idling.",
                            modelCommonality = "Engine accessory drive check",
                            followUpQuestion = "Does the rattle speed up when you rev slightly in Park?",
                            suggestedQuickReplies = listOf("Yes, gets louder"),
                            logSummary = "Engine bay rattle reported; requested video with audio of belt area.",
                            requestedCaptureType = RequestedCaptureType.VIDEO,
                            requestedCaptureReason = "Record a 10-second video near the passenger-side drive belt while the engine idles so I can see and hear the pulley."
                        )
                    )
                }
            }
        }

        val chatVm = VehicleAiChatViewModel(
            repository = store,
            currentUserId = "user_proactive",
            vehicleId = id,
            aiService = fakeAi
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleAiChatScreen(
                    viewModel = chatVm,
                    onNavigateBack = {},
                    onOpenIssueLog = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { chatVm.uiState.value.vehicle != null }

        // Open Frame 6 '+' menu -> tap Camera icon -> verify video picker option is inside media chooser sheet
        composeRule.onNodeWithTag("chat_plus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_attach_photo_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_attach_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_media_sheet_close_button").performClick()
        composeRule.waitForIdle()

        // Ask about rattling sound -> AI responds with proactive VIDEO request card
        composeRule.onNodeWithTag("chat_message_input").performTextInput("Weird rattle from engine bay")
        composeRule.onNodeWithTag("chat_send_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            chatVm.uiState.value.messages.size == 2 && !chatVm.uiState.value.isSending
        }

        composeRule.onNodeWithTag("chat_ai_capture_request_1").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_ai_capture_action_1").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_ai_capture_secondary_1").assertIsDisplayed()
        composeRule.onNodeWithText("Record Video").assertIsDisplayed()

        // Attach a video clip and send -> verifies AttachmentKind.VIDEO saved to log
        chatVm.attachVideo("content://media/external/video/engine_clip.mp4", "video/mp4", "engine_clip.mp4")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_pending_attachment_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_send_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            chatVm.uiState.value.messages.size == 4 && !chatVm.uiState.value.isSending
        }

        val logs = runBlocking { store.observeDiagnosisLogs("user_proactive", id).first() }
        assertEquals(AttachmentKind.VIDEO, logs.first().resolvedAttachmentKind())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `register vehicle form shows 4-angle framing guide in English and Thai and assigns photos per angle`() {
        val store = InMemoryPreviewVehicleDataSource()
        val formVm = VehicleFormViewModel(
            repository = store,
            currentUserId = "preview_guided_user",
            editingVehicleId = null
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleFormScreen(
                    viewModel = formVm,
                    onNavigateBack = {},
                    onVehicleSaved = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Verify hero banner, all 4 angle slots, preview stage, action bar, and white form container render cleanly
        composeRule.onNodeWithTag("form_hero_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("form_angle_slot_FRONT").assertIsDisplayed()
        composeRule.onNodeWithTag("form_angle_slot_SIDE_LEFT").assertIsDisplayed()
        composeRule.onNodeWithTag("form_angle_slot_SIDE_RIGHT").assertIsDisplayed()
        composeRule.onNodeWithTag("form_angle_slot_REAR").assertIsDisplayed()
        composeRule.onNodeWithTag("form_photo_preview_stage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("form_angle_guide_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("form_take_photo_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("form_white_container").performScrollTo().assertIsDisplayed()

        // Select Left Side and verify framing guide updates
        composeRule.onNodeWithTag("form_angle_slot_SIDE_LEFT").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(VehicleAngle.SIDE_LEFT, formVm.uiState.value.selectedCaptureAngle)
        composeRule.onNodeWithText("Left Side Profile (90°)").performScrollTo().assertIsDisplayed()

        // Select Rear and switch language to Thai via Settings
        composeRule.onNodeWithTag("form_angle_slot_REAR").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_option_th").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("มุมด้านหลังรถ (180°)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ด้านหลังรถ").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `vehicle diagnostics hub displays vehicle selector, honest disconnected status, subpage routes, and read-only policy`() {
        val store = InMemoryPreviewVehicleDataSource()
        val vehicleId = runBlocking {
            store.createVehicle(
                "diag_user",
                VehicleDraft(
                    manufacturer = "Nissan",
                    model = "GT-R",
                    modelYearInput = "2024",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "3.8L Twin-Turbo V6",
                    transmission = "6-Speed Dual-Clutch",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "6200",
                    market = "Japan",
                    setAsPrimary = true
                )
            ).getOrThrow()
        }

        val diagVm = DiagnosticsViewModel(
            repository = store,
            currentUserId = "diag_user",
            initialVehicleId = vehicleId
        )

        var openedConnectMethod: ObdConnectionMethod? = null
        var openedScan = false
        var openedResults = false
        var openedHistory = false
        var openedReport = false
        var openedExplain = false

        composeRule.setContent {
            MotoraTheme {
                DiagnosticsScreen(
                    viewModel = diagVm,
                    onNavigateBack = {},
                    onAddVehicle = {},
                    onOpenConnect = { openedConnectMethod = it },
                    onOpenScan = { openedScan = true },
                    onOpenResults = { openedResults = true },
                    onOpenHistory = { openedHistory = true },
                    onOpenReport = { openedReport = true },
                    onOpenExplain = { openedExplain = true }
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !diagVm.uiState.value.isLoadingVehicles }

        composeRule.onNodeWithTag("diagnostics_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_selected_vehicle_title").assertTextEquals("2024 Nissan GT-R")
        composeRule.onNodeWithTag("what_is_obd_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_see_explains_button").performScrollTo().performClick()
        assertTrue(openedExplain)

        composeRule.onNodeWithTag("diagnostics_connection_status_text")
            .performScrollTo()
            .assertTextEquals("Disconnected")
        composeRule.onNodeWithTag("diagnostics_bluetooth_option_row").performScrollTo().performClick()
        assertEquals(ObdConnectionMethod.BLUETOOTH, openedConnectMethod)
        composeRule.onNodeWithTag("diagnostics_wifi_option_row").performScrollTo().performClick()
        assertEquals(ObdConnectionMethod.WIFI, openedConnectMethod)

        composeRule.onNodeWithTag("diagnostics_open_scan_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("diagnostics_nav_results_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("diagnostics_nav_history_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("diagnostics_nav_report_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("diagnostics_readonly_notice").performScrollTo().assertIsDisplayed()

        assertTrue(openedScan)
        assertTrue(openedResults)
        assertTrue(openedHistory)
        assertTrue(openedReport)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `obd explain screen renders beginner guide, port diagram, steps, and quick connect actions`() {
        var chosenMethod: ObdConnectionMethod? = null
        composeRule.setContent {
            MotoraTheme {
                ObdExplainScreen(
                    onNavigateBack = {},
                    onOpenConnect = { chosenMethod = it }
                )
            }
        }

        composeRule.onNodeWithTag("obd_explain_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("obd_explain_banner_image").assertIsDisplayed()
        composeRule.onNodeWithTag("obd_port_location_diagram").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("obd_explain_connect_bt_button").performScrollTo().performClick()
        assertEquals(ObdConnectionMethod.BLUETOOTH, chosenMethod)
        composeRule.onNodeWithTag("obd_explain_connect_wifi_button").performScrollTo().performClick()
        assertEquals(ObdConnectionMethod.WIFI, chosenMethod)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `garage header navigates to diagnostics and all five diagnostic subpages render cleanly`() {
        val store = InMemoryPreviewVehicleDataSource()
        val garageVm = GarageViewModel(store, "diag_nav_user", "Driver")
        var diagnosticsClicked = false

        var activePage by mutableStateOf(0)
        val connectVm = DiagnosticsConnectViewModel(ObdConnectionManager())

        composeRule.setContent {
            MotoraTheme {
                when (activePage) {
                    0 -> GarageScreen(
                        viewModel = garageVm,
                        ownerDisplayName = "Driver",
                        onAddVehicle = {},
                        onOpenVehicleOverview = {},
                        onEditVehicle = {},
                        onOpenDiagnostics = { diagnosticsClicked = true },
                        onSignOut = {}
                    )
                    1 -> DiagnosticsConnectScreen(
                        viewModel = connectVm,
                        selectedVehicleLabel = "2024 Nissan GT-R",
                        onNavigateBack = {}
                    )
                    2 -> DiagnosticsScanScreen(selectedVehicleLabel = "2024 Nissan GT-R", onNavigateBack = {})
                    3 -> DiagnosticsResultsScreen(selectedVehicleLabel = "2024 Nissan GT-R", onNavigateBack = {})
                    4 -> DiagnosticsHistoryScreen(selectedVehicleLabel = "2024 Nissan GT-R", onNavigateBack = {})
                    else -> DiagnosticsReportScreen(selectedVehicleLabel = "2024 Nissan GT-R", onNavigateBack = {})
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { garageVm.uiState.value is GarageUiState.Ready }
        composeRule.onNodeWithTag("empty_nav_diagnostics_button").assertIsDisplayed().performClick()
        assertTrue(diagnosticsClicked)

        // Verify dedicated subpages render independently
        activePage = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_connect_screen").assertIsDisplayed()

        activePage = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_scan_screen").assertIsDisplayed()

        activePage = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_results_screen").assertIsDisplayed()

        activePage = 4
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_history_screen").assertIsDisplayed()

        activePage = 5
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_report_screen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `phase 2 connection setup switches Wi-Fi and Bluetooth architectures, validates Wi-Fi state, and never simulates a live connection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeWifiPort = object : WifiHardwarePort {
            override fun isWifiSupported(context: Context) = true
            override fun isWifiEnabled(context: Context) = true
            override fun isWifiConnected(context: Context) = false
            override fun currentSsidLabel(context: Context): String? = null
        }

        val fakeBluetoothPort = object : BluetoothHardwarePort {
            override fun requiredPermissions() = listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun missingPermissions(context: Context) = emptyList<String>()
            override fun isBluetoothSupported(context: Context) = true
            override fun isBluetoothEnabled(context: Context) = true
            override fun listPairedDevices(context: Context) = listOf(
                BluetoothDeviceEntry(
                    address = "AA:BB:CC:11:22:33",
                    displayName = "OBDLink MX+ (Paired)",
                    architecture = BluetoothArchitecture.CLASSIC_SPP,
                    isBonded = true
                ),
                BluetoothDeviceEntry(
                    address = "DD:EE:FF:44:55:66",
                    displayName = "Veepeak BLE+",
                    architecture = BluetoothArchitecture.BLE_GATT,
                    isBonded = false
                )
            )
            override fun startBleScan(
                context: Context,
                onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
                onScanError: (String) -> Unit
            ) = true
            override fun stopBleScan(context: Context) {}
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(fakeWifiPort),
            bluetoothAdapter = BluetoothObdAdapter(fakeBluetoothPort)
        )
        val connectVm = DiagnosticsConnectViewModel(manager)

        composeRule.setContent {
            MotoraTheme {
                DiagnosticsConnectScreen(
                    viewModel = connectVm,
                    selectedVehicleLabel = "2024 Nissan GT-R",
                    onNavigateBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Wi-Fi section displays Thai labels, adapter overview info card & endpoint fields
        composeRule.onNodeWithTag("wifi_connection_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_adapter_info_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_host_input").performScrollTo().assertIsDisplayed()

        // Attempt connect while not joined to OBD-II Wi-Fi hotspot -> honest Error (never Connected)
        composeRule.onNodeWithTag("wifi_connect_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            manager.state.value.connectionStatus is ObdConnectionStatus.Error
        }
        assertTrue(manager.state.value.connectionStatus is ObdConnectionStatus.Error)
        assertFalse(manager.state.value.isConnected)

        // Switch to Bluetooth -> verify adapter overview info card & Classic SPP device listed, then switch to BLE GATT
        composeRule.onNodeWithTag("connect_method_BLUETOOTH").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bluetooth_connection_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bluetooth_adapter_info_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bluetooth_device_row_AA:BB:CC:11:22:33").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("bt_arch_BLE_GATT").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bluetooth_device_row_DD:EE:FF:44:55:66").performScrollTo().assertIsDisplayed()

        // Attempt BLE connect without physical adapter -> honest hardware Error (never fake Connected)
        composeRule.onNodeWithTag("bluetooth_connect_button").performScrollTo().performClick()
        try {
            composeRule.waitUntil(timeoutMillis = 8_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val s = manager.state.value.connectionStatus
                s is ObdConnectionStatus.Error || s is ObdConnectionStatus.PermissionRequired
            }
        } catch (t: Throwable) {
            throw AssertionError(
                "Timed out! method=${manager.state.value.activeMethod}, status=${manager.state.value.connectionStatus}, btSelected=${manager.state.value.bluetoothState.selectedDeviceAddress}, devices=${manager.state.value.bluetoothState.devices.map { it.address }}",
                t
            )
        }
        val finalStatus = manager.state.value.connectionStatus
        assertTrue(
            "Expected Error or PermissionRequired without physical BLE adapter, got $finalStatus",
            finalStatus is ObdConnectionStatus.Error || finalStatus is ObdConnectionStatus.PermissionRequired
        )
        assertFalse(manager.state.value.isConnected)

        // Clear / Disconnect resets back to Disconnected cleanly
        connectVm.disconnect(context)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            manager.state.value.connectionStatus is ObdConnectionStatus.Disconnected
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `floating MACK button appears on Garage and Diagnostics and navigates into MACK AI screen`() {
        val previewStore = com.example.data.repository.InMemoryPreviewVehicleDataSource()
        runBlocking {
            previewStore.ensureUserProfile("preview_local_session", "Preview Driver")
            previewStore.createVehicle(
                "preview_local_session",
                VehicleDraft(
                    manufacturer = "Nissan",
                    model = "GT-R",
                    modelYearInput = "2024",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "3.8L Twin-Turbo V6",
                    transmission = "6-Speed Dual-Clutch",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "12500",
                    market = "Thailand"
                )
            ).getOrThrow()
        }
        composeRule.setContent {
            MotoraTheme {
                MotoraAppShell(
                    currentUserId = "preview_local_session",
                    ownerDisplayName = "Preview Driver",
                    isPreviewMode = true,
                    dataSourceProvider = { previewStore },
                    onSignOut = {}
                )
            }
        }

        // Wait until Garage displays the registered vehicle and Floating MACK mascot appears
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("floating_mack_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("floating_mack_button").assertIsDisplayed()

        // Navigate to Diagnostics hub -> Floating MACK remains available & vehicle cutout stage is present
        composeRule.onNodeWithTag("nav_diagnostics_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("diagnostics_vehicle_cutout_stage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("diagnostics_vehicle_cutout_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_mack_button").assertIsDisplayed()

        // Tap Floating MACK -> opens MACK AI screen (MEET MACK) and hides the floating overlay while inside MACK
        composeRule.onNodeWithTag("floating_mack_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("chat_mack_landing_hero").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_mack_landing_hero").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("floating_mack_button").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `background remover strips uniform white studio backdrop cleanly to transparent PNG`() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val srcFile = java.io.File(appContext.cacheDir, "test_car_white_studio.png")
        val bmp = android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
        // Fill solid white studio background
        bmp.eraseColor(android.graphics.Color.WHITE)
        // Paint dark car silhouette in center [25..95, 25..60]
        for (y in 25..60) {
            for (x in 25..95) {
                bmp.setPixel(x, y, android.graphics.Color.rgb(42, 48, 56))
            }
        }
        java.io.FileOutputStream(srcFile).use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val resultUri = com.example.ui.components.VehicleBackgroundRemover.extractVehicleCutoutUri(
            appContext,
            srcFile.toURI().toString()
        )
        assertTrue(resultUri.endsWith("_v2_cutout.png"))

        val outFile = java.io.File(java.net.URI.create(resultUri))
        assertTrue(outFile.exists())
        val outBmp = android.graphics.BitmapFactory.decodeFile(outFile.absolutePath)
        val cornerAlpha = (outBmp.getPixel(0, 0) ushr 24) and 0xFF
        val centerAlpha = (outBmp.getPixel(outBmp.width / 2, outBmp.height / 2) ushr 24) and 0xFF
        assertEquals(0, cornerAlpha)
        assertEquals(255, centerAlpha)
        outBmp.recycle()
    }

    @Test
    fun `country watermark normalizes America and United States to USA and formats manufacturer markets cleanly`() {
        assertEquals("USA", com.example.ui.garage.normalizeCountryWatermark("America"))
        assertEquals("USA", com.example.ui.garage.normalizeCountryWatermark("United States"))
        assertEquals("USA", com.example.ui.garage.normalizeCountryWatermark("United States of America"))
        assertEquals("JAPAN", com.example.ui.garage.normalizeCountryWatermark("Japan"))
        assertEquals("SWEDEN", com.example.ui.garage.normalizeCountryWatermark("Sweden / EU"))
        assertEquals("THAILAND", com.example.ui.garage.normalizeCountryWatermark("Thailand"))

        val rivianUsVehicle = Vehicle(
            id = "v_us",
            manufacturer = "Rivian",
            model = "R1T",
            modelYear = 2025,
            market = "America"
        )
        assertEquals("USA", com.example.ui.garage.resolveGarageCountryWatermark(rivianUsVehicle))

        val fallbackToyotaJapan = Vehicle(
            id = "v_jp",
            manufacturer = "Toyota",
            model = "RAV4",
            modelYear = 2024,
            market = ""
        )
        assertEquals("JAPAN", com.example.ui.garage.resolveGarageCountryWatermark(fallbackToyotaJapan))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `redesigned garage displays shutter hero banner, View Car showcase card with USA watermark, and clickable Add New Cars slot`() {
        val store = FakeVehicleDataSource(activeAuthenticatedUserId = "user_garage_ui")
        val carId = runBlocking {
            store.createVehicle(
                "user_garage_ui",
                VehicleDraft(
                    manufacturer = "Nissan",
                    model = "GT-R",
                    modelYearInput = "2024",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "3.8L Twin-Turbo V6",
                    transmission = "6-Speed Dual-Clutch",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "3400",
                    market = "United States"
                )
            ).getOrThrow()
        }

        val garageVm = GarageViewModel(store, "user_garage_ui", "Driver")
        runBlocking { garageVm.uiState.first { it is GarageUiState.Ready } }

        var openedOverviewId: String? = null
        var addVehicleClicked = false

        composeRule.setContent {
            MotoraTheme {
                GarageScreen(
                    viewModel = garageVm,
                    ownerDisplayName = "Driver",
                    onAddVehicle = { addVehicleClicked = true },
                    onOpenVehicleOverview = { openedOverviewId = it },
                    onEditVehicle = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("garage_hero_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("vehicle_card_$carId").assertIsDisplayed()
        composeRule.onNodeWithTag("vehicle_card_default_photo_$carId", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("vehicle_country_watermark_$carId", useUnmergedTree = true)
            .assertTextEquals("USA")
        composeRule.onNodeWithTag("card_overview_$carId").assertIsDisplayed().performClick()
        assertEquals(carId, openedOverviewId)

        composeRule.onNodeWithTag("garage_compact_list")
            .performScrollToNode(hasTestTag("add_vehicle_button"))
        composeRule.onNodeWithTag("add_vehicle_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(addVehicleClicked)
    }

    @Test
    fun `system privacy lock preserves photo pixels visually while stripping GPS location EXIF tags`() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        val plateBmp = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        plateBmp.eraseColor(android.graphics.Color.rgb(36, 42, 52))
        val whitePlateColor = android.graphics.Color.rgb(245, 246, 248)
        for (y in 152 until 184) {
            for (x in 110 until 210) {
                plateBmp.setPixel(x, y, whitePlateColor)
            }
        }

        val srcFile = java.io.File(appContext.cacheDir, "test_car_gps_exif.jpg")
        java.io.FileOutputStream(srcFile).use { out ->
            plateBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
        plateBmp.recycle()

        // Embed GPS coordinates into EXIF
        val exifBefore = android.media.ExifInterface(srcFile.absolutePath)
        exifBefore.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, "13/1,45/1,2268/100")
        exifBefore.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        exifBefore.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, "100/1,30/1,648/100")
        exifBefore.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        exifBefore.saveAttributes()

        val result = com.example.ui.components.LicensePlatePrivacyProtector.protectVehiclePhoto(
            context = appContext,
            sourceUri = srcFile.toURI().toString()
        )
        assertEquals("License plates must NOT be visually covered", 0, result.platesCoveredCount)
        assertTrue("Expected GPS location EXIF tags to be stripped", result.locationMetadataStripped)

        val safeFile = java.io.File(java.net.URI.create(result.outputUri))
        assertTrue(safeFile.exists())
        val exifAfter = android.media.ExifInterface(safeFile.absolutePath)
        org.junit.Assert.assertNull(exifAfter.getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE))
        org.junit.Assert.assertNull(exifAfter.getAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE))

        // Photo pixels remain completely intact (bright plate area is not covered)
        val outBmp = android.graphics.BitmapFactory.decodeFile(safeFile.absolutePath)
        val pixel = outBmp.getPixel(160, 168)
        val brightness = (android.graphics.Color.red(pixel) +
            android.graphics.Color.green(pixel) +
            android.graphics.Color.blue(pixel)) / 3
        assertTrue("Expected plate pixels to remain visually untouched (> 200), got $brightness", brightness > 200)
        outBmp.recycle()
    }

    @Test
    fun `system plate privacy lock sanitizes plate citations and GPS coordinates from AI outputs`() {
        val leakedVisionJson = """
            {
              "manufacturer": "Mitsubishi",
              "model": "Mirage",
              "modelYearGuess": "2022",
              "bodyStyle": "HATCHBACK",
              "fuelTypeHint": "Petrol / Gasoline",
              "trimOrBadgeHints": "GLX License plate number: 1AB-9988",
              "exteriorSummary": "Silver hatchback at GPS: 13.7563, 100.5018 with ทะเบียนรถ กข 1234.",
              "angleCoverageOk": true,
              "qualityFeedback": ""
            }
        """.trimIndent()

        val parsed = com.example.data.ai.FirebaseVehicleVisionAnalyzer.parseVisionJson(leakedVisionJson)
        assertFalse(parsed.trimOrBadgeHints.contains("1AB-9988"))
        assertFalse(parsed.exteriorSummary.contains("13.7563"))
        assertFalse(parsed.exteriorSummary.contains("1234"))
        assertTrue(parsed.exteriorSummary.contains("Silver hatchback"))
        assertTrue(com.example.ui.components.LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_EN.contains("NEVER"))
        assertTrue(com.example.ui.components.LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_TH.contains("ห้าม"))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `photo capture guide red privacy note recommends covering license plate in Thai and English`() {
        LanguagePreferenceStore.setLanguageImmediate(AppLanguage.THAI)
        composeRule.setContent {
            MotoraTheme {
                PhotoCaptureGuideScreen(
                    onNavigateBack = {},
                    onContinueToPhotoCapture = {}
                )
            }
        }
        composeRule.waitForIdle()
        val thaiPrivacyNode = composeRule.onNodeWithTag("guide_privacy_note").performScrollTo().assertIsDisplayed()
        val thaiText = thaiPrivacyNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
        assertTrue("Expected Thai red guidance to recommend covering plate if possible, got: $thaiText", thaiText.contains("ถ้าเป็นไปได้"))

        LanguagePreferenceStore.setLanguageImmediate(AppLanguage.ENGLISH)
        composeRule.waitForIdle()
        val enText = composeRule.onNodeWithTag("guide_privacy_note").performScrollTo().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
        assertTrue("Expected English red guidance to recommend covering plate if possible, got: $enText", enText.contains("If possible"))
    }

    @Test
    fun `vehicle studio silhouette renders all body styles cleanly inside wide Garage proportions`() {
        composeRule.setContent {
            MotoraTheme {
                androidx.compose.foundation.layout.Column {
                    BodyStyle.entries.forEach { style ->
                        Box(modifier = Modifier.size(width = 394.dp, height = 148.dp)) {
                            com.example.ui.components.VehicleStudioSilhouette(
                                bodyStyle = style,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
