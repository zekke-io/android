package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.feature.settings.DeviceItem
import zekke.android.feature.settings.DeviceRemoval
import zekke.android.feature.settings.DevicesLoad
import zekke.android.feature.settings.DevicesServices
import zekke.android.feature.settings.DevicesWrite
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.SessionGuard
import zekke.core.account.AccountServices
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.PhraseMismatchException
import zekke.core.account.readVerifiedChain
import zekke.core.account.removeOtherDevices
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.items.ItemContext
import zekke.core.keyrings.listDevices
import zekke.core.keys.isValidMnemonic
import zekke.core.keys.mnemonicWordSuggestions
import zekke.core.scopes.Scope
import zekke.core.scopes.parseScopeList
import zekke.core.sharing.SharingService
import zekke.core.sharing.setDeviceName

fun removalOutcomeOf(error: Exception): DeviceRemoval = when (error) {
    is PhraseMismatchException -> DeviceRemoval.PhraseMismatch
    is NetworkError -> DeviceRemoval.Offline
    is ApiError -> if (error.isRateLimited) DeviceRemoval.RateLimited(error.retryAfterSeconds) else DeviceRemoval.Failed
    is ChainNotVerifiedException -> DeviceRemoval.ChainProblem
    is DeviceVaultUnavailableException -> DeviceRemoval.PhoneLocked
    else -> DeviceRemoval.Failed
}

class CoreDevicesServices(
    private val accounts: AccountServices,
    private val sharing: SharingService,
    private val calls: ItemCalls,
    private val context: ItemContext,
    private val guard: SessionGuard,
    private val rotations: KeyRotationEvents,
    private val suggestionsFor: (CharArray) -> List<String> = ::mnemonicWordSuggestions,
    private val phraseIsValid: (List<CharArray>) -> Boolean = ::isValidMnemonic,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : DevicesServices {
    override val fullDevice: Boolean get() = context.session.isFullDevice

    override val canName: Boolean get() = context.session.holds(Scope.SHARING)

    override fun phraseSuggestions(prefix: CharArray): List<String> = suggestionsFor(prefix)

    override fun isValidPhrase(words: List<CharArray>): Boolean = phraseIsValid(words)

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()

    override suspend fun load(): DevicesLoad = calls.read {
        val session = context.session
        val devices = listDevices(context.api).devices
        val names = if (canName) sharing.loadAddressBook().devices else emptyMap()
        val verified = try {
            readVerifiedChain(accounts, session.userAddress, session.rootPublicKey, session.deviceId)
            true
        } catch (_: ChainNotVerifiedException) {
            false
        }
        DevicesLoad.Loaded(
            devices.map { device ->
                DeviceItem(
                    id = device.id,
                    name = names[device.id]?.name?.trim()?.takeIf { it.isNotEmpty() },
                    thisDevice = device.id == session.deviceId,
                    scopes = parseScopeList(device.scopes),
                    addedAt = device.createdAt,
                )
            },
            verified,
        )
    }.fold({ it }, { DevicesLoad.Failed(it) })

    override suspend fun rename(id: String, name: String): DevicesWrite =
        calls.write { sharing.editAddressBook(setDeviceName(id, name)) }.fold({ DevicesWrite.Done }, { DevicesWrite.Failed(it) })

    override suspend fun remove(id: String, words: List<CharArray>): DeviceRemoval = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            withContext(work) {
                try {
                    removeOtherDevices(accounts, words, listOf(id))
                    rotations.rotated()
                    DeviceRemoval.Removed
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    removalOutcomeOf(error)
                }
            }
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }
}
