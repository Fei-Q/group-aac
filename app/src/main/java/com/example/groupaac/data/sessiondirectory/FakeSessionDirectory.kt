package com.example.groupaac.data.sessiondirectory

import com.example.groupaac.model.SessionStatus

class FakeSessionDirectory(
    private val nowProvider: () -> Long = System::currentTimeMillis
) : SessionDirectory {

    private val entriesByCode =
        linkedMapOf<String, SessionDirectoryEntry>()

    fun seed(entry: SessionDirectoryEntry) {
        val code = normalizeJoinCodeOrNull(entry.joinCode)
            ?: error(
                "Fake directory entry must have an eight-digit code."
            )

        entriesByCode[code] = entry.copy(
            joinCode = formatJoinCode(code)
        )
    }

    override suspend fun register(
        entry: SessionDirectoryEntry
    ): RegisterSessionResult {
        val code = normalizeJoinCodeOrNull(entry.joinCode)
            ?: return RegisterSessionResult.Failure(
                "Session code must contain exactly eight digits."
            )

        val existing = entriesByCode[code]

        if (
            existing != null &&
            existing.sessionId != entry.sessionId
        ) {
            return RegisterSessionResult.CodeTaken
        }

        val normalized = entry.copy(
            joinCode = formatJoinCode(code)
        )

        entriesByCode[code] = normalized

        return RegisterSessionResult.Registered(normalized)
    }

    override suspend fun resolve(
        joinCode: String
    ): ResolveJoinCodeResult {
        val code = normalizeJoinCodeOrNull(joinCode)
            ?: return ResolveJoinCodeResult.InvalidCode

        val entry = entriesByCode[code]
            ?: return ResolveJoinCodeResult.NotFound

        if (
            entry.protocolVersion !=
            SESSION_DIRECTORY_PROTOCOL_VERSION
        ) {
            return ResolveJoinCodeResult.UnsupportedVersion(
                entry.protocolVersion
            )
        }

        if (entry.expiresAt <= nowProvider()) {
            return ResolveJoinCodeResult.Expired
        }

        if (entry.status != SessionStatus.LIVE) {
            return ResolveJoinCodeResult.NotLive
        }

        return ResolveJoinCodeResult.Found(entry)
    }

    override suspend fun update(
        entry: SessionDirectoryEntry
    ): UpdateDirectoryEntryResult {
        val code = normalizeJoinCodeOrNull(entry.joinCode)
            ?: return UpdateDirectoryEntryResult.Failure(
                "Session code must contain exactly eight digits."
            )

        val existing = entriesByCode[code]
            ?: return UpdateDirectoryEntryResult.NotFound

        if (existing.sessionId != entry.sessionId) {
            return UpdateDirectoryEntryResult.SessionMismatch
        }

        val normalized = entry.copy(
            joinCode = formatJoinCode(code)
        )

        entriesByCode[code] = normalized

        return UpdateDirectoryEntryResult.Updated(normalized)
    }

    override suspend fun remove(
        joinCode: String,
        sessionId: String
    ): RemoveDirectoryEntryResult {
        val code = normalizeJoinCodeOrNull(joinCode)
            ?: return RemoveDirectoryEntryResult.NotFound

        val existing = entriesByCode[code]
            ?: return RemoveDirectoryEntryResult.NotFound

        if (existing.sessionId != sessionId) {
            return RemoveDirectoryEntryResult.SessionMismatch
        }

        entriesByCode.remove(code)

        return RemoveDirectoryEntryResult.Removed
    }
}