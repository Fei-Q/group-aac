package com.example.groupaac.data.sessiondirectory

import com.example.groupaac.model.DisplayMode
import com.example.groupaac.model.SessionStatus

const val SESSION_DIRECTORY_PROTOCOL_VERSION = 1

data class SessionDirectoryEntry(
    val protocolVersion: Int = SESSION_DIRECTORY_PROTOCOL_VERSION,
    val joinCode: String,
    val sessionId: String,
    val sessionName: String,
    val hostUserId: String,
    val displayId: String,
    val status: SessionStatus,
    val displayMode: DisplayMode,
    val createdAt: Long,
    val actualStartedAt: Long,
    val expiresAt: Long
)

sealed interface RegisterSessionResult {
    data class Registered(
        val entry: SessionDirectoryEntry
    ) : RegisterSessionResult

    data object CodeTaken : RegisterSessionResult

    data class Failure(
        val message: String
    ) : RegisterSessionResult
}

sealed interface ResolveJoinCodeResult {
    data class Found(
        val entry: SessionDirectoryEntry
    ) : ResolveJoinCodeResult

    data object InvalidCode : ResolveJoinCodeResult
    data object NotFound : ResolveJoinCodeResult
    data object NotLive : ResolveJoinCodeResult
    data object Expired : ResolveJoinCodeResult

    data class UnsupportedVersion(
        val version: Int
    ) : ResolveJoinCodeResult

    data class Failure(
        val message: String
    ) : ResolveJoinCodeResult
}

sealed interface UpdateDirectoryEntryResult {
    data class Updated(
        val entry: SessionDirectoryEntry
    ) : UpdateDirectoryEntryResult

    data object NotFound : UpdateDirectoryEntryResult
    data object SessionMismatch : UpdateDirectoryEntryResult

    data class Failure(
        val message: String
    ) : UpdateDirectoryEntryResult
}

sealed interface RemoveDirectoryEntryResult {
    data object Removed : RemoveDirectoryEntryResult
    data object NotFound : RemoveDirectoryEntryResult
    data object SessionMismatch : RemoveDirectoryEntryResult

    data class Failure(
        val message: String
    ) : RemoveDirectoryEntryResult
}

interface SessionDirectory {
    suspend fun register(
        entry: SessionDirectoryEntry
    ): RegisterSessionResult

    suspend fun resolve(
        joinCode: String
    ): ResolveJoinCodeResult

    suspend fun update(
        entry: SessionDirectoryEntry
    ): UpdateDirectoryEntryResult

    suspend fun remove(
        joinCode: String,
        sessionId: String
    ): RemoveDirectoryEntryResult
}

fun normalizeJoinCodeOrNull(raw: String): String? {
    val digits = raw.filter(Char::isDigit)
    return digits.takeIf { it.length == 8 }
}

fun formatJoinCode(raw: String): String {
    val digits = normalizeJoinCodeOrNull(raw)
        ?: error("Session code must contain exactly eight digits.")

    return "${digits.take(4)}-${digits.drop(4)}"
}