package com.example.groupaac.data.pi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockPiClient : PiClient {
    override suspend fun joinSession(request: PiJoinRequest) = Unit
    override suspend fun sendMessage(payload: PiMessagePayload) = Unit
    override suspend fun sendSignal(payload: PiSignalPayload) = Unit
    override suspend fun sendDisplayCommand(command: DisplayCommand) = Unit
    override fun observeSessionEvents(sessionId: String): Flow<PiSessionEvent> = flowOf(PiSessionEvent.Connected)
}
