package com.example.groupaac.data.realtime.protocol

object RealtimeChannels {

    fun public(
        sessionId: String
    ): String =
        "session.$sessionId.public"

    fun facilitator(
        sessionId: String
    ): String =
        "session.$sessionId.facilitator"

    fun privateUser(
        sessionId: String,
        userId: String
    ): String =
        "session.$sessionId.$userId"

    fun display(
        sessionId: String
    ): String =
        "session.$sessionId.display"

    fun displayEvents(
        sessionId: String
    ): String =
        "session.$sessionId.display.events"

    /**
     * Commands sent from Android to one physical Pi.
     */
    fun displayControl(
        displayId: String
    ): String =
        "display.$displayId.control"

    /**
     * Pairing, binding, failure, and unbinding responses sent by one Pi.
     */
    fun displayDeviceEvents(
        displayId: String
    ): String =
        "display.$displayId.events"
}