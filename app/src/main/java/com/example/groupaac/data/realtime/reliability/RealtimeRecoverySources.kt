package com.example.groupaac.data.realtime.reliability

import com.example.groupaac.data.realtime.protocol.ReceivedRealtimeEvent
import kotlinx.serialization.json.JsonObject

interface RealtimeHistoryDataSource {
    suspend fun fetchAfter(
        channel: String,
        afterTimetoken: Long?,
        limit: Int
    ): List<ReceivedRealtimeEvent>
}

interface SessionSnapshotDataSource {
    suspend fun fetchSnapshot(
        sessionId: String,
        requesterUserId: String
    ): JsonObject?
}
