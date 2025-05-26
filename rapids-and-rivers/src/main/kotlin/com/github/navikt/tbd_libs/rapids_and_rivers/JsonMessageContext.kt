package com.github.navikt.tbd_libs.rapids_and_rivers

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage

internal class JsonMessageContext(
    private val rapidsConnection: MessageContext,
    private val packet: JsonMessage
) : MessageContext {
    override fun publish(message: String) {
        rapidsConnection.publish(populateStandardFields(message))
    }

    override fun publish(key: String, message: String) {
        rapidsConnection.publish(key, populateStandardFields(message))
    }

    override fun publishBulk(messages: List<OutgoingMessage>) {
        rapidsConnection.publishBulk(
            messages.map {
                it.copy(body = populateStandardFields(it.body))
            }
        )
    }

    private fun populateStandardFields(message: String) =
        JsonMessage.populateStandardFields(packet, message)

    override fun rapidName(): String {
        return rapidsConnection.rapidName()
    }
}
