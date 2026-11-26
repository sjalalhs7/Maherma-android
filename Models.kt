package com.maherma.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Channel { SMS, WHATSAPP }
enum class ConnectivityPanel { WIFI, BLUETOOTH, VOLUME }

/** Low-level device operations executed by ActionExecutor. Tools map their arguments onto these. */
@Serializable
sealed interface PhoneAction {
    @Serializable @SerialName("open_app") data class OpenApp(val appName: String) : PhoneAction
    @Serializable @SerialName("dial") data class Dial(val contact: String) : PhoneAction
    @Serializable @SerialName("timer") data class SetTimer(val seconds: Int, val label: String = "") : PhoneAction
    @Serializable @SerialName("alarm") data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : PhoneAction
    @Serializable @SerialName("event") data class AddEvent(val title: String, val startIso: String? = null) : PhoneAction
    @Serializable @SerialName("lookup") data class LookupContact(val query: String) : PhoneAction
    @Serializable @SerialName("torch") data class Flashlight(val on: Boolean) : PhoneAction
    @Serializable @SerialName("volume") data class SetVolume(val percent: Int) : PhoneAction
    @Serializable @SerialName("panel") data class OpenPanel(val panel: ConnectivityPanel) : PhoneAction
    @Serializable @SerialName("message") data class SendMessage(
        val channel: Channel, val recipient: String, val body: String
    ) : PhoneAction
}

fun PhoneAction.summary(): String = when (this) {
    is PhoneAction.Dial -> "Call $contact"
    is PhoneAction.SendMessage ->
        "Message ${if (channel == Channel.WHATSAPP) "on WhatsApp" else "by SMS"} to $recipient:\n“$body”"
    else -> toString()
}

// ---- Chat/UI models ----
enum class ItemKind { USER, ASSISTANT, ACTION, CARD, ERROR }
enum class ActionStatus { AWAITING_CONFIRM, RUNNING, NEEDS_PERMISSION, NEEDS_INPUT, DONE, FAILED, CANCELLED }

data class ChatItem(
    val id: Long,
    val threadId: Long,
    val kind: ItemKind,
    val text: String,
    val status: ActionStatus? = null,
    val payload: String? = null,
    val detail: String? = null,
    val chips: List<String> = emptyList(),
    val createdAt: Long,
)

data class ChatThread(val id: Long, val title: String, val updatedAt: Long)

/** One text turn: role is "user" or "model". */
data class Turn(val role: String, val text: String)
