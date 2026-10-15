package zekke.android.items

import java.time.Instant
import kotlinx.coroutines.CancellationException
import zekke.android.feature.settings.ExtensionLinkServices
import zekke.android.feature.settings.PairingOpen
import zekke.android.feature.settings.PairingPoll
import zekke.android.feature.settings.PairingWrite
import zekke.core.items.ItemContext
import zekke.core.pairing.ClaimedDevice
import zekke.core.pairing.PairingStatus
import zekke.core.pairing.cancelPairing
import zekke.core.pairing.claimedDevice
import zekke.core.pairing.displayFingerprint
import zekke.core.pairing.getPairing
import zekke.core.pairing.linkClaimedDevice
import zekke.core.pairing.openPairing
import zekke.core.pairing.ownerFingerprint
import zekke.core.scopes.Scope
import zekke.core.sharing.SharingService
import zekke.core.sharing.setDeviceName

class CoreExtensionLinkServices(
    private val sharing: SharingService,
    private val calls: ItemCalls,
    private val context: ItemContext,
) : ExtensionLinkServices {
    private var code = ""
    private var claimed: ClaimedDevice? = null

    override val canLink: Boolean get() = context.session.isFullDevice && context.session.holds(Scope.PASSWORDS)

    override val canNameExtension: Boolean get() = context.session.holds(Scope.SHARING)

    override suspend fun open(): PairingOpen = calls.write {
        val opened = openPairing(context.api)
        code = opened.code
        claimed = null
        PairingOpen.Opened(opened.id, zekke.core.pairing.formatPairingCode(opened.code), Instant.parse(opened.expiresAt).toEpochMilli())
    }.fold({ it }, { PairingOpen.Failed(it) })

    override suspend fun poll(id: String): PairingPoll = calls.read {
        val record = getPairing(context.api, id)
        when (record.status) {
            PairingStatus.OPEN -> PairingPoll.Waiting
            PairingStatus.CLAIMED -> {
                val device = claimedDevice(record)
                claimed = device
                PairingPoll.Claimed(displayFingerprint(ownerFingerprint(context, code, device)))
            }
            PairingStatus.LINKED -> PairingPoll.Linked
            PairingStatus.EXPIRED -> PairingPoll.Expired
            else -> PairingPoll.Cancelled
        }
    }.fold({ it }, { PairingPoll.Failed(it) })

    override suspend fun link(id: String, name: String): PairingWrite {
        val device = claimed ?: return PairingWrite.Failed(zekke.android.ui.components.ItemFailure.Failed)
        val result = calls.write {
            linkClaimedDevice(context, id, device)
            if (name.isNotEmpty() && canNameExtension) {
                try {
                    sharing.editAddressBook(setDeviceName(device.deviceId, name))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            }
        }
        return result.fold(
            {
                claimed = null
                PairingWrite.Done
            },
            { PairingWrite.Failed(it) },
        )
    }

    override suspend fun cancel(id: String): PairingWrite {
        claimed = null
        return calls.write { cancelPairing(context.api, id) }.fold({ PairingWrite.Done }, { PairingWrite.Failed(it) })
    }
}
