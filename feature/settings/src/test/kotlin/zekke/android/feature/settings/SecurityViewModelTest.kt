package zekke.android.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.core.pin.PinRejection

private class FakeSecurityServices(initial: Int = 15) : SecurityServices {
    private var current = initial
    override val idleMinutes: Int get() = current
    val saved = mutableListOf<Int>()
    val pins = mutableListOf<String>()
    var result: DevicePinChange = DevicePinChange.Done
    var gate: CompletableDeferred<Unit>? = null

    override fun setIdleMinutes(minutes: Int) {
        saved += minutes
        current = minutes
    }

    override suspend fun changeDevicePin(pin: CharArray): DevicePinChange {
        pins += String(pin)
        gate?.await()
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {
    private val services = FakeSecurityServices()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(): SecurityViewModel = ViewModelProvider.create(
        ViewModelStore(),
        viewModelFactory { initializer { SecurityViewModel(services) } },
    )[SecurityViewModel::class]

    private fun SecurityViewModel.type(pin: String, confirmation: String = pin) {
        pinChanged(pin)
        confirmationChanged(confirmation)
    }

    @Test
    fun theStoredWindowIsShownAndAChoiceIsSavedAtOnce() {
        val model = model()
        assertEquals(15, model.state.value.idleMinutes)
        model.chooseIdle(5)
        assertEquals(listOf(5), services.saved)
        assertEquals(5, model.state.value.idleMinutes)
    }

    @Test
    fun onlyTheOfferedWindowsAreAccepted() {
        val model = model()
        model.chooseIdle(7)
        model.chooseIdle(0)
        assertTrue(services.saved.isEmpty())
        assertEquals(15, model.state.value.idleMinutes)
    }

    @Test
    fun aValidPinIsChangedAndTheFieldsAreCleared() {
        val model = model()
        model.type("395172")
        model.submit()
        assertEquals(listOf("395172"), services.pins)
        assertEquals(DevicePinChange.Done, model.state.value.outcome)
        assertEquals("", model.state.value.pin)
        assertEquals("", model.state.value.confirmation)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun aWeakPinIsRefusedWithItsReasonAndNeverSent() {
        val model = model()
        model.type("111111")
        model.submit()
        assertEquals(PinProblem.Rejected(PinRejection.REPEATING_DIGIT), model.state.value.problem)
        assertEquals("", model.state.value.confirmation)
        model.type("123456")
        model.submit()
        assertEquals(PinProblem.Rejected(PinRejection.ASCENDING_SEQUENCE), model.state.value.problem)
        assertTrue(services.pins.isEmpty())
    }

    @Test
    fun aMismatchIsRefusedAndNeverSent() {
        val model = model()
        model.type("395172", "395173")
        model.submit()
        assertEquals(PinProblem.Mismatch, model.state.value.problem)
        assertTrue(services.pins.isEmpty())
    }

    @Test
    fun everyFailureIsKeptWithTheFieldsSoItCanBeRetried() {
        val model = model()
        listOf(DevicePinChange.Offline, DevicePinChange.RateLimited(30), DevicePinChange.PhoneLocked, DevicePinChange.Failed).forEach { outcome ->
            services.result = outcome
            model.type("395172")
            model.submit()
            assertEquals(outcome, model.state.value.outcome)
            assertEquals("395172", model.state.value.pin)
            assertFalse(model.state.value.busy)
            model.dismissOutcome()
            assertNull(model.state.value.outcome)
        }
    }

    @Test
    fun oneChangeAtATimeAndTheFieldsAreFrozenWhileItRuns() {
        val model = model()
        services.gate = CompletableDeferred()
        model.type("395172")
        model.submit()
        model.submit()
        model.pinChanged("999999")
        assertEquals(1, services.pins.size)
        assertEquals("395172", model.state.value.pin)
        assertTrue(model.state.value.busy)
        services.gate!!.complete(Unit)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun anUnexpectedExceptionIsAFailure() {
        val broken = object : SecurityServices by FakeSecurityServices() {
            override suspend fun changeDevicePin(pin: CharArray): DevicePinChange = throw IllegalStateException()
        }
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { SecurityViewModel(broken) } },
        )[SecurityViewModel::class]
        model.type("395172")
        model.submit()
        assertEquals(DevicePinChange.Failed, model.state.value.outcome)
    }
}
