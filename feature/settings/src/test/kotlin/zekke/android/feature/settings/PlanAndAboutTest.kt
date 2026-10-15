package zekke.android.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.components.StorageReading
import zekke.android.ui.theme.ZekkeTheme

private class FakePlanServices : PlanServices {
    var result: PlanLoad = PlanLoad.Loaded(PlanInfo("free", PlanStates.FREE, null, false, null, 0), StorageReading(100, 100, 500))
    var loads = 0

    override suspend fun load(): PlanLoad {
        loads++
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanAndAboutTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakePlanServices()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        val model = PlanViewModel(services)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                PlanScreen(state, model)
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    @Test
    fun theFreePlanShowsItsNameStorageAndNoTrash() {
        show()
        text("Free").assertExists()
        text("Deleted items are not kept.").assertExists()
        text("Plans are bought and managed in the web app").assertExists()
    }

    @Test
    fun anActivePlanShowsWhatItIsPaidUntilAndThatItRenews() {
        services.result = PlanLoad.Loaded(PlanInfo("premium_1", PlanStates.ACTIVE, "2027-01-15T00:00:00Z", true, null, 30), null)
        show()
        text("Premium 1").assertExists()
        text("Paid until").assertExists()
        text("renews on its own").assertExists()
        text("Deleted items are kept for 30 days.").assertExists()
    }

    @Test
    fun aPlanInGraceWarnsWithTheEndDateAndWhatHappensAfter() {
        services.result = PlanLoad.Loaded(PlanInfo("premium_1", PlanStates.GRACE, null, false, "2026-11-01T00:00:00Z", 30), null)
        show()
        text("Your plan has ended. Your files stay readable until").assertExists()
        text("never deleted").assertExists()
    }

    @Test
    fun aFailedLoadSaysWhy() {
        services.result = PlanLoad.Failed(ItemFailure.Offline)
        show()
        text("This phone is offline.").assertExists()
    }

    @Test
    fun theDateIsFormattedForThePhoneAndAnUnreadableOneIsAbsent() {
        assertEquals("Jan 15, 2027", planDate("2027-01-15T12:00:00Z", java.util.Locale.US, java.time.ZoneId.of("UTC")))
        assertNull(planDate("nonsense"))
        assertNull(planDate(null))
    }

    @Test
    fun aboutShowsTheVersionAndTheNoticesItIsGiven() {
        compose.setContent {
            ZekkeTheme { AboutScreen("0.1.0") { Text("an update is available") } }
        }
        text("Version 0.1.0").assertExists()
        text("an update is available").assertExists()
    }
}
