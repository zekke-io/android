package zekke.android.feature.vault

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.feature.tabs.TabsLoad
import zekke.android.ui.theme.ZekkeTheme
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.ManifestScope
import zekke.core.folders.emptyFolderManifest
import zekke.core.secrets.VaultView

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VaultScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private var shared: String? = null
    private var onShare: ((String) -> Unit)? = null
    private val services = FakeVaultServices()
    private val tabs = object : FolderTabsServices {
        override val canDeleteItems = true
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var manifest: FolderManifest = emptyFolderManifest(ManifestScope.SECRETS.rules, "2026-10-01T00:00:00.000Z")
        override suspend fun load() = TabsLoad.Loaded(manifest)
        override suspend fun edit(edit: FolderEdit): TabsLoad {
            manifest = edit.apply(manifest, ManifestScope.SECRETS.rules)
            return TabsLoad.Loaded(manifest)
        }
        override suspend fun reset() = TabsLoad.Loaded(manifest)
    }
    private var ids = 0
    private lateinit var model: VaultViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.view = VaultView(listOf(vaultRow("1", "Wifi", "hunter2"), vaultRow("2", "Bank PIN", "4321")), listOf(deletedSecret("9", "Old")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(vault: FakeVaultServices = services) {
        model = VaultViewModel(vault, tabs) { "tab-${++ids}" }
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                VaultScreen(state, model, onShare)
            }
        }
    }

    @Test
    fun theNamesShowAndTheValuesStayMaskedUntilTheToggle() {
        show()
        compose.onNodeWithText("Wifi").assertExists()
        compose.onNodeWithText("hunter2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show values").performClick()
        compose.onNodeWithText("hunter2").assertExists()
        compose.onNodeWithContentDescription("Hide values").performClick()
        compose.onNodeWithText("hunter2").assertDoesNotExist()
    }

    @Test
    fun anEmptyVaultSaysSo() {
        services.view = VaultView(emptyList(), emptyList())
        show()
        compose.onNodeWithText("Nothing stored yet", substring = true).assertExists()
    }

    @Test
    fun theTabsShowWithHomeAndTheirCounts() {
        show()
        compose.onNodeWithText("Home").assertExists()
    }

    @Test
    fun theAddButtonNeedsANameAndAValueAndNextMovesToTheValue() {
        show()
        compose.onNodeWithContentDescription("Add a secret").performClick()
        compose.onNodeWithText("Add secret").assertIsNotEnabled()
        compose.onNodeWithText("Name").performTextInput("Router")
        compose.onNodeWithText("Name").performImeAction()
        compose.onNodeWithText("Value").assertIsFocused()
        compose.onNodeWithText("Value").performTextInput("s3cret")
        compose.onNodeWithText("Add secret").assertIsEnabled()
        compose.onNodeWithText("Add secret").performClick()
        assertEquals("Router", services.created.single().name)
    }

    @Test
    fun aRowOpensItsSheetWithDeleteOnlyOnAFullDevice() {
        show()
        compose.onNodeWithText("Wifi").performClick()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete this secret?").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        compose.onNodeWithText("Delete this secret?").assertDoesNotExist()
    }

    @Test
    fun aDeviceThatIsNotFullIsOfferedNoDelete() {
        val limited = FakeVaultServices(canDelete = false)
        limited.view = services.view
        show(limited)
        compose.onNodeWithText("Wifi").performClick()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun theDeletedPageRestoresAndAsksBeforePurging() {
        show()
        compose.onNodeWithText("Recently deleted").performClick()
        compose.onNodeWithText("Old").assertExists()
        compose.onNodeWithText("Restore").performClick()
        assertEquals(listOf("9"), services.restored)
        compose.onNodeWithText("Delete permanently").performScrollTo().performClick()
        compose.onNodeWithText("Delete this secret permanently?").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        assertEquals(emptyList<List<String>>(), services.purged)
    }

    @Test
    fun aSecretOffersShareOnlyWhenTheHostProvidesTheSlot() {
        show()
        compose.onNodeWithText("Wifi").performClick()
        compose.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun shareHandsTheSecretToTheHostAndClosesTheDetail() {
        onShare = { shared = it }
        show()
        compose.onNodeWithText("Wifi").performClick()
        compose.onNodeWithText("Share").performScrollTo().performClick()
        assertEquals("1", shared)
        compose.onNodeWithText("Copy value").assertDoesNotExist()
    }
}
