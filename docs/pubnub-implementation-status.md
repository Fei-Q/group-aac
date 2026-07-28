# PubNub Implementation Status

## Stage 1 - Baseline and audit

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-realtime-foundation`
Starting commit: `5be1ff5 Add PubNub SDK and configuration properties`

### Baseline repository state

- Working tree was clean before stage work.
- Root `.gitignore` did not yet ignore `pubnub.properties`.
- Root `build/` output and IDE-local directories were already ignored.
- A root `local.properties` file exists and remains unmodified.

### Baseline build and test status

- `./gradlew :app:assembleDebug`
  - Failed in `:app:kspDebugKotlin`.
  - PubNub and transitive Kotlin artifacts were compiled with Kotlin metadata `2.2.0` while the project expected `2.0.0`.
  - A KSP incremental failure also appeared: missing generated file `UserDao_Impl.java`.
- `./gradlew :app:testDebugUnitTest`
  - Failed in `:app:kspDebugKotlin`.
  - Same Kotlin metadata incompatibility blocked unit tests before execution.
- Connected tests were not part of this baseline stage.

### Architecture map before implementation

#### Identity and account creation

- [`UserEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserEntity.kt) used generated `id` as the primary key, retained deprecated account-level `role`, and tracked `lastLoginAt`.
- [`AccountRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt) created accounts with `IdUtils.newId()` rather than user-created immutable UIDs.
- [`AccountViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/account/AccountViewModel.kt) depended on `AccountRepository`, which in turn depended directly on `UserDao` rather than a registration abstraction.
- [`UserSettingsEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserSettingsEntity.kt) still contained deprecated account-level role/default-role compatibility fields and monitor defaults that need to move cleanly into the new schema.

#### Room schema and persistence

- [`AppDatabase`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/AppDatabase.kt) was on Room schema version `5` with old migrations still present.
- `exportSchema` was `false`, while an old exported schema file still existed at [`app/schemas/com.example.groupaac.data.AppDatabase/1.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/1.json).
- Active session persistence was per-user through [`ActiveSessionStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/session/ActiveSessionStore.kt), keyed by generated user ID.
- One app-install database file (`group_aac.db`) was already used, which aligns with the plan.

#### Session and repository layer

- [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt) owned local session lifecycle, join approval logic, membership, and active-session activation.
- Session membership already used per-session roles (`PARTICIPANT`, `FACILITATOR`, `HOST`), which aligns with the plan.
- `SessionEntity` lacked session-level realtime/display settings such as `DisplayMode`, timestamps for updates, and richer lifecycle metadata.
- Join requests already had a dedicated entity/DAO path, but the data model still needs cleanup around immutable UID usage and future realtime decisions.

#### Realtime and Raspberry Pi integration

- [`PiClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/pi/PiClient.kt) was a narrow transport abstraction focused on direct Pi operations rather than a general session realtime transport.
- [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt) wired a single app-wide `MockPiClient`, not one client per active account.
- Repositories (`SessionRepository`, `MessageRepository`, `SignalRepository`) called the transport directly instead of persisting outbox work and routing through a realtime manager.

#### Signals and Resolve behavior

- [`StatusSignalEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/StatusSignalEntity.kt) still modeled `state` plus `resolvedAt`, which does not match the new create/snooze/clear-only design.
- [`SignalRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SignalRepository.kt) still exposed:
  - `resolveSignal`
  - `resolveSignalsForUser`
  - global `snoozeSignal` / `unsnoozeSignal`
- Current snooze semantics were not facilitator-specific, so one facilitator could hide a signal for others.

#### Display controls

- Display commands existed through the Pi transport, but there was no explicit realtime protocol envelope, outbox, cursor, or acknowledgement persistence.
- The current model does not yet separate:
  - session display command channel
  - session display event channel
  - persistent display state in Room
- The planned explicit `display.show_message`, `display.clear`, `display.pin_message`, and acknowledgement handling were not yet wired end to end.

#### Tests before implementation

- Repository tests existed for local session logic in [`SessionRepositoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/SessionRepositoryTest.kt).
- UI tests existed for account/home/settings/session screens.
- There were no serializer/parser/routing tests for a realtime protocol yet.
- There were no reliability tests for outbox, deduplication, cursor recovery, or display command ordering yet.

### Stage 1 notes for upcoming work

- First repair target is the Kotlin/PubNub compatibility issue blocking all builds.
- Stage 2 will require a schema reset, migration removal, identity rewrite, and foreign-key cleanup across entities, DAOs, repositories, and tests.
- Stage 4 onward will replace direct Pi transport calls with a broader realtime client boundary and fake implementation.

## Stage 2 - Fresh identity and Room schema

Status: complete

### Implemented

- Reset Room to schema version `1` in [`AppDatabase`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/AppDatabase.kt).
- Removed legacy migration chain and enabled destructive recreation for the development schema reset.
- Reworked `users` to store the immutable account primary key in the `uid` database column while preserving a stable Kotlin `id` property for the rest of the codebase transition.
- Removed deprecated account-role and last-login persistence from [`UserEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserEntity.kt).
- Removed deprecated `defaultRole` compatibility state from [`UserSettingsEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserSettingsEntity.kt).
- Added session-level [`DisplayMode`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/DisplayMode.kt) to [`SessionEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/SessionEntity.kt) with default `AUTO_LATEST`.
- Updated Room queries and joins to use the new `uid` column in users.
- Regenerated the exported schema at [`app/schemas/com.example.groupaac.data.AppDatabase/1.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/1.json).

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- The Kotlin property name transition is intentionally incremental: the database primary key is now `uid`, while much of the existing app code still accesses the same value through `UserEntity.id`.
- Date-sensitive home-screen tests were updated to use dates in the future relative to Tuesday, July 28, 2026.

## Stage 3 - Registration abstraction

Status: complete

### Implemented

- Added [`UserIdRegistry`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/account/UserIdRegistry.kt) with:
  - `CreateAccountRequest`
  - `CreateAccountResult`
  - `LocalUserIdRegistry`
- Enforced UID validation with the required regex: `^[a-z0-9][a-z0-9_]{2,23}$`.
- Account creation now inserts `UserEntity` and `UserSettingsEntity` transactionally via `withTransaction`.
- Duplicate primary-key insertion is converted to `AlreadyTaken`.
- [`AccountRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt) now depends on `UserIdRegistry` instead of creating IDs directly.
- Updated account UI/view-model flow to collect a user-created UID plus display name and surface validation/duplicate-account errors.

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- This is still a local-only registry implementation by design.
- The interface boundary is now ready for a future remote UID registry and token/session authority work.

## Stage 4 - PubNub foundation

Status: complete

### Implemented

- Root `pubnub.properties` remains the only supported source of PubNub keys, and the root `.gitignore` now ignores it.
- Removed the old tracked `app/pubnub.properties` file.
- Added generated `BuildConfig` fields for:
  - `PUBNUB_PUBLISH_KEY`
  - `PUBNUB_SUBSCRIBE_KEY`
- Added [`PubNubRuntimeConfig`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/PubNubRuntimeConfig.kt) and `PubNubConfigProvider` to read runtime PubNub config from `BuildConfig`.
- Added a new broader realtime transport boundary:
  - [`SessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionRealtimeClient.kt)
  - [`FakeSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/FakeSessionRealtimeClient.kt)
- Added account-scoped realtime lifecycle management in [`RealtimeClientManager`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/RealtimeClientManager.kt).
- Added future auth/session boundaries:
  - `PubNubTokenProvider`
  - `SessionAuthority`
- Wired account creation, account switching, and sign-out to activate/deactivate the active realtime client.
- Added [`DelegatingPiClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/DelegatingPiClient.kt) so existing repositories preserve their local behavior while the new client lifecycle foundation sits underneath.
- Added stage-4 lifecycle tests in:
  - [`RealtimeClientManagerTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RealtimeClientManagerTest.kt)
  - [`RecordingRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RecordingRealtimeClient.kt)

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
  - One transient KSP cache failure occurred first under `app/build/kspCaches/debug/backups`; rerunning after regeneration succeeded.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- Repositories still speak through the legacy `PiClient` method surface, but that client is now explicitly marked deprecated and is backed by the new lifecycle-managed realtime boundary.
- The transport implementation is still fake/local at this stage by design; actual PubNub event publishing/subscription begins in the next stages.

## Stage 5 - Realtime protocol

Status: complete

### Implemented

- Added channel helper functions in [`RealtimeChannels`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeChannels.kt) for:
  - `session.<sessionId>.public`
  - `session.<sessionId>.facilitator`
  - `session.<sessionId>.<userId>`
  - `session.<sessionId>.display`
  - `session.<sessionId>.display.events`
  - `display.<displayId>.control`
- Added canonical protocol models in [`RealtimeEvent`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEvent.kt):
  - `RealtimeEvent`
  - `ReceivedRealtimeEvent`
- Added allowed event-type constants in [`RealtimeEventTypes`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventTypes.kt).
- Added manual serializer/parser support in [`RealtimeEventCodec`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventCodec.kt) using `JsonObject` payloads and omission of nullable fields when absent.
- Added channel routing in [`RealtimeEventRouter`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEventRouter.kt).
- Added protocol tests in [`RealtimeProtocolTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/protocol/RealtimeProtocolTest.kt) covering:
  - channel helper output
  - serializer/parser round-trip
  - omission of null transport fields
  - preservation of `inReplyToEventId` and `expiresAt`
  - supported/unsupported channel routing

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- The protocol layer intentionally does not introduce `schemaVersion`, app-generated sequence numbers, `message.edited`, `message.received`, `aac.signal.updated`, `aac.signal.resolved`, or `display.take_down`.
- Timetoken ordering metadata is represented through `ReceivedRealtimeEvent`; actual cursor/deduplication logic follows in stage 6.

## Stage 6 - Reliability

Status: complete

### Implemented

- Added persistence for realtime reliability state in Room schema version `2`:
  - [`OutboxEventEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/OutboxEventEntity.kt)
  - [`ProcessedEventEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/ProcessedEventEntity.kt)
  - [`ChannelCursorEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/ChannelCursorEntity.kt)
  - [`DisplayStateEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/DisplayStateEntity.kt)
- Added [`OutboxEventState`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/OutboxEventState.kt) and Room converters for outbox lifecycle storage.
- Added [`ReliabilityDao`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/dao/ReliabilityDao.kt) with transactional processed-event and cursor recording.
- Added [`RealtimeReliabilityStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt) to provide:
  - local-first outbox persistence
  - bounded retry/backoff scheduling
  - processed-event deduplication by `eventId`
  - channel cursor updates from PubNub timetokens
  - display-command ordering and stale-command rejection
- Added recovery interfaces for future persisted history/snapshot replay in [`RealtimeRecoverySources`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeRecoverySources.kt).
- Added reliability unit coverage in [`RealtimeReliabilityStoreTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStoreTest.kt) for:
  - retry backoff caps
  - processed-event deduplication
  - stale display-command rejection
  - expired outbox exclusion

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Failed first because the new Room-backed reliability test was missing the Robolectric runner during `ApplicationProvider` setup.
  - Passed on Tuesday, July 28, 2026, after aligning the new test with the repo's existing Robolectric unit-test pattern.

### Notes

- The reliability layer is now persisted locally, but repositories are not yet publishing through it; stage 7 wires the session flows onto these primitives.
- Display-command freshness is currently enforced through PubNub timetoken ordering, which matches the implementation plan's received-order metadata requirement.

## Stage 7 - Session synchronization

Status: complete

### Implemented

- Extended [`SessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionRealtimeClient.kt) with generic canonical-event publishing and per-channel observation while preserving the existing Pi-oriented calls used by the current app flow.
- Upgraded [`FakeSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/FakeSessionRealtimeClient.kt) into an in-memory realtime bus that records published canonical events with synthetic timetokens for unit verification.
- Added a repository-facing synchronization boundary in [`SessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/SessionRealtimeSync.kt), plus a safe no-op default to preserve local behavior when realtime is unavailable.
- Added canonical session/message payload DTOs and snapshot payload support in [`RealtimePayloads`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/RealtimePayloads.kt).
- Added [`DefaultSessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt) to:
  - publish session lifecycle, membership, facilitator request/decision, message, and targeted snapshot events to the required channels
  - enqueue and mark outbox events through the stage-6 reliability store
  - apply incoming canonical events back into Room with processed-event deduplication and channel-cursor updates
- Wired repositories to publish canonical events after their local-first writes:
  - [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt)
  - [`MessageRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/MessageRepository.kt)
- Added synchronization unit coverage in [`DefaultSessionRealtimeSyncTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt).

### Verification

- `./gradlew :app:assembleDebug`
  - Failed first because the new sync layer used helper names that did not match the existing `RealtimeChannels` and `RealtimeEventCodec` APIs.
  - Failed second because the Kotlin serialization plugin had not yet been declared in the root Gradle plugin block for this project.
  - Passed on Tuesday, July 28, 2026, after aligning the sync layer with the repo’s actual protocol helpers and registering the serialization plugin.
- `./gradlew :app:testDebugUnitTest`
  - Failed first because [`RecordingRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RecordingRealtimeClient.kt) still implemented the older realtime-client surface.
  - Failed second because the initial outbox assertion checked the retry queue rather than the persisted sent event.
  - Passed on Tuesday, July 28, 2026, after updating the test double and asserting against the stored sent outbox row.

### Notes

- Local Room behavior remains the UI source of truth and still works when realtime synchronization is a no-op.
- Live subscription collectors and reconnect replay orchestration are still intentionally thin at this stage; the new sync service provides the publish/apply foundation that stages 8 and 9 now build on.

## Stage 8 - AAC signals

Status: complete

### Implemented

- Reworked signal persistence to the create/snooze/clear-only model:
  - [`StatusSignalEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/StatusSignalEntity.kt) now stores `clearedAt` instead of `resolvedAt`.
  - [`SignalState`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/SignalState.kt) no longer includes `RESOLVED`.
  - Added facilitator-scoped snooze persistence in [`SignalSnoozeEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/SignalSnoozeEntity.kt).
- Rebuilt signal queries and mutations in [`StatusSignalDao`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/dao/StatusSignalDao.kt) so that:
  - active signals are based on underlying `CURRENT` rows only
  - facilitator-specific snooze state is projected through a join on `signal_snoozes`
  - clearing a participant signal also removes related snoozes
- Updated [`SignalRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SignalRepository.kt) to:
  - observe active signals per facilitator UID
  - replace global snooze with facilitator-specific snooze/unsnooze
  - remove all resolve paths
  - clear snoozes when a signal is cleared
- Updated facilitator flow in [`FacilitatorViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/facilitator/FacilitatorViewModel.kt) so snooze toggles are scoped to the active facilitator and participant clearing uses the clear-only path.
- Removed leftover resolved-signal settings from [`UserSettingsEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/UserSettingsEntity.kt).
- Wired the previously built realtime sync layer into runtime construction in [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt) using:
  - [`RealtimeReliabilityStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt)
  - [`DefaultSessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt)
- Added stage-8 repository coverage in [`SignalRepositoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/SignalRepositoryTest.kt) for:
  - facilitator-specific snooze visibility
  - clearing signals removing snoozes
- Regenerated the exported Room schema at [`app/schemas/com.example.groupaac.data.AppDatabase/3.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/3.json).

### Verification

- `./gradlew :app:assembleDebug`
  - Failed first because [`SignalRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SignalRepository.kt) still needed the `SignalState` import after the signal cleanup.
  - Passed on Tuesday, July 28, 2026, after restoring that import.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- The deprecated `ACTIVE` enum entry remains only as a defensive fallback for older local rows during development, but current signal writes and queries now use `CURRENT`, `SNOOZED`, and `CLEARED`.
- Resolve has been removed from the signal DAO/repository/view-model flow, but the facilitator participant UI still expresses the action as snooze vs clear behavior rather than introducing additional new controls at this stage.

## Stage 9 - Shared display

Status: complete

### Implemented

- Expanded [`DisplayCommand`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/pi/DisplayCommand.kt) to cover:
  - `ShowMessage`
  - `RestoreMessage`
  - `PinMessage`
  - `UnpinMessage`
  - `Clear`
- Extended the realtime display contract in:
  - [`SessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/SessionRealtimeSync.kt)
  - [`RealtimePayloads`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/RealtimePayloads.kt)
  - [`DefaultSessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt)
- Added shared-display payload handling for:
  - explicit `display.show_message` and `display.restore_message` commands
  - `display.pin_message` and `display.unpin_message`
  - `display.clear`
  - Pi acknowledgement/state events updating Room display state via `inReplyToEventId`
- Reworked [`MessageRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/MessageRepository.kt) so that:
  - eligible group messages auto-display only when the session is `AUTO_LATEST`
  - facilitator/private messages are not auto-displayed
  - pinned display state blocks automatic replacement
  - Clear also removes the pin
  - display state is observable from Room
  - manual Show, Restore, Pin/Unpin, and Clear all route through explicit display commands
- Updated facilitator display state/UI wiring in:
  - [`FacilitatorUiState`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/facilitator/FacilitatorUiState.kt)
  - [`FacilitatorViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/facilitator/FacilitatorViewModel.kt)
  - [`FacilitatorHomeScreen`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/facilitator/FacilitatorHomeScreen.kt)
  - [`FacilitatorInSessionNavGraph`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/navigation/FacilitatorInSessionNavGraph.kt)
  - [`SessionLogScreen`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/facilitator/SessionLogScreen.kt)
- Added shared-display tests:
  - [`DefaultSessionRealtimeSyncTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt) now covers Pi display acknowledgements updating Room display state.
  - [`MessageRepositoryDisplayTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/MessageRepositoryDisplayTest.kt) covers auto-display eligibility and Clear removing pin state.

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.

### Notes

- New sessions already defaulted to `AUTO_LATEST` from stage 2, so this stage builds on that existing session-level default rather than changing it again.
- The manual display controls now distinguish Show vs Restore based on message display history and place Pin/Unpin directly beside Clear in the currently showing pane.

## Stage 10 - Verification and handoff

Status: complete

### Stage commits

- Stage 4: `d25fae8` - `Lay PubNub realtime foundation`
- Stage 5: `e26256f` - `Add realtime protocol contract`
- Stage 6: `68dfd26` - `Add realtime reliability persistence`
- Stage 7: `4f087ff` - `Add session realtime synchronization`
- Stage 8: `34bdbe6` - `Refine AAC signal snooze semantics`
- Stage 9: `2f3954b` - `Implement shared display controls`

### Final verification

- Emulator
  - Serial: `emulator-5554`
  - Boot state on Tuesday, July 28, 2026: `1`
  - `adb -s emulator-5554 devices -l` reported:
    - `emulator-5554 device product:sdk_gphone16k_arm64 model:sdk_gphone16k_arm64 device:emu64a16k`
- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:connectedDebugAndroidTest --stacktrace`
  - Failed on Tuesday, July 28, 2026.
  - Instrumentation started `0` tests on `Resizable_Experimental(AVD) - 17`.
  - The run crashed before executing tests because `androidx.test.runner.AndroidJUnitRunner` was not found in the test process classpath.
  - Gradle report path:
    - [`app/build/reports/androidTests/connected/debug/index.html`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/build/reports/androidTests/connected/debug/index.html)

### Pi handoff artifacts

- Raspberry Pi source is not present in this repository.
- Added Android-to-Pi contract documentation in:

## Live Realtime Next Steps - Stage 4

Status: complete

Date: Tuesday, July 28, 2026
Branch: `feature/pubnub-live-integration`
Starting commit: `5d504c5 Implement live PubNub transport`

### Implemented

- Added [`SessionSubscriptionCoordinator`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinator.kt) to own runtime session-scoped PubNub subscriptions.
- Started and stopped subscriptions automatically from active account/session state for:
  - participant `public` and private-user channels
  - facilitator and host `public`, `facilitator`, private-user, and `display.events` channels
  - facilitator-request pending state with the requester private channel plus session public channel
- Wired startup restoration through [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt) so a persisted active session resubscribes after realtime client activation on launch.
- Routed every subscribed incoming event through a single pipeline:
  - channel route parsing
  - obvious channel/session/user mismatch rejection
  - handoff to [`SessionRealtimeSync.applyIncoming()`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/SessionRealtimeSync.kt) for expiry rejection, deduplication, Room application, and processed-event/cursor recording
- Fed realtime connection state from the coordinator into [`SessionCoordinatorViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/session/SessionCoordinatorViewModel.kt) instead of relying on the old unused manual realtime callback methods.
- Removed the two leftover legacy session-activation `PiClient.joinSession(...)` calls from [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt) so account/session subscription ownership stays with the realtime coordinator.
- Added Stage 4 unit coverage in [`SessionSubscriptionCoordinatorTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinatorTest.kt) for:
  - correct channels by role
  - stop/unsubscribe behavior
  - account-switch cleanup
  - active-session restoration
  - incoming event delivery into `applyIncoming()`
  - connection-state propagation
  - pending facilitator-request private-channel tracking

### Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.
- Focused verification during implementation:
  - `./gradlew :app:testDebugUnitTest --tests "com.example.groupaac.data.realtime.SessionSubscriptionCoordinatorTest"`
    - Failed first while the new coordinator test harness was still using a non-starting background scope.
    - Passed on Tuesday, July 28, 2026, after moving the tests onto an explicit unconfined test scope with deterministic cleanup.

### Notes

- This stage does not yet enforce full role authorization; it only rejects obvious session/channel mismatches as requested.
- A live two-client PubNub exchange smoke test was not run in this environment during this stage, so runtime verification here is build plus unit-test based.
  - [`docs/pi-display-protocol-contract.md`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/docs/pi-display-protocol-contract.md)
  - [`docs/pi-display-protocol-fixtures.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/docs/pi-display-protocol-fixtures.json)

### Unresolved issues

- Connected Android tests currently fail before test execution because the instrumentation runner class is missing at runtime.
- The realtime transport is still backed by the fake in-memory client, not a live PubNub session client.
- The legacy `PiClient` compatibility layer is still present while repositories transition onto the broader realtime boundary.
- There is still no Raspberry Pi consumer implementation in this repository to verify the full display command/acknowledgement loop against real device code.

### Deferred deployment work

- Implement the real PubNub-backed `SessionRealtimeClient` with subscription lifecycle, reconnect replay, and channel recovery.
- Introduce backend-issued PubNub token/auth flows for production instead of local shared-key development setup.
- Replace the fake Android-side display transport with a real Raspberry Pi consumer and acknowledgement publisher.
- Repair the Android instrumentation packaging so connected tests can execute instead of crashing during runner startup.

### Manual PubNub setup

1. Create a root-level `pubnub.properties` file.
2. Add:
   - `PUBNUB_PUBLISH_KEY=pub-c-...`
   - `PUBNUB_SUBSCRIBE_KEY=sub-c-...`
3. Keep `pubnub.properties` out of version control.
4. Rebuild with `./gradlew :app:assembleDebug`.

### Manual smoke-test steps

1. Confirm the emulator is booted:
   - `adb -s emulator-5554 shell getprop sys.boot_completed`
2. Launch two app instances with distinct local UIDs.
3. Create or join the same session on both devices.
4. Send a group message and confirm:
   - it persists locally
   - it appears in the session log
   - it auto-displays only when the session is `AUTO_LATEST` and the display is not pinned
5. Send a facilitator/private message and confirm it does not auto-display.
6. Use Show, Restore, Pin/Unpin, and Clear in the facilitator log screen and confirm Room display state updates locally.
7. Send an AAC signal from a participant and confirm one facilitator can snooze it without hiding it from another facilitator account.

## Live Realtime Stage 1 - Baseline, schema cleanup, and test infrastructure

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`
Base branch: `feature/pubnub-realtime-foundation`

### Implemented

- Renamed the Kotlin account primary-key property from `UserEntity.id` to `UserEntity.uid` and updated active usage across repositories, ViewModels, navigation flows, settings screens, previews, and unit tests.
- Added shared UID handling in [`UserIdValidator`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/account/UserIdValidator.kt) for:
  - normalization
  - input sanitization
  - regex validation against `^[a-z0-9][a-z0-9_]{2,23}$`
- Replaced the pipe-delimited create-account callback with a typed callback carrying:
  - `uid`
  - `displayName`
  - `homeExperience`
- Tightened create-account UX in [`CreateAccountScreen`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/account/CreateAccountScreen.kt):
  - unsupported characters are ignored
  - UID input is capped at 24 characters
  - inline validation feedback is shown
  - Create is enabled only when UID and display name are valid
  - success consumption now happens from a `LaunchedEffect` instead of direct composition-time state mutation
- Reset the disposable Room baseline in [`AppDatabase`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/AppDatabase.kt) to schema version `1` and limited destructive recreation to debug builds only.
- Removed obsolete exported schema snapshots:
  - `app/schemas/com.example.groupaac.data.AppDatabase/2.json`
  - `app/schemas/com.example.groupaac.data.AppDatabase/3.json`
- Regenerated the current exported baseline schema at [`app/schemas/com.example.groupaac.data.AppDatabase/1.json`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/schemas/com.example.groupaac.data.AppDatabase/1.json).
- Added missing AndroidX instrumentation dependencies in [`app/build.gradle.kts`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/build.gradle.kts).
- Added a connected smoke test in [`MainActivitySmokeTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/androidTest/java/com/example/groupaac/MainActivitySmokeTest.kt) that launches `MainActivity` through `ActivityScenario` and asserts the app renders a root content view.

### Verification

- Emulator
  - Serial: `emulator-5554`
  - Boot state on Tuesday, July 28, 2026: `1`
- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:connectedDebugAndroidTest --stacktrace`
  - Passed on Tuesday, July 28, 2026.
  - Executed `1` test on `Resizable_Experimental(AVD) - 17`.

### Notes

- This Stage 1 cleanup supersedes the earlier transitional note that `UserEntity.id` remained in active Kotlin use on the prior branch.
- App data should be cleared after checking out this branch because the disposable Room baseline was intentionally reset to version `1`.
- Prior PubNub foundation and later realtime stages were left intact; only Stage 1 cleanup work for the live-integration branch was applied here.

## Live Realtime Stage 2 - Authoritative session directory

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`

### Implemented

- Added Android session-directory contracts in [`SessionDirectory`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/sessiondirectory/SessionDirectory.kt) with typed results for create, resolve, update, end, and cancel flows.
- Added [`RemoteSessionDirectory`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/sessiondirectory/RemoteSessionDirectory.kt), [`HttpGroupAacApi`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/sessiondirectory/HttpGroupAacApi.kt), and deterministic [`FakeSessionDirectory`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/sessiondirectory/FakeSessionDirectory.kt).
- Added `SESSION_DIRECTORY_BASE_URL` BuildConfig wiring in [`app/build.gradle.kts`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/build.gradle.kts) and runtime injection through [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt).
- Reworked [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt) so that:
  - session creation uses the backend-authoritative session ID and join code
  - join-code resolution goes through `SessionDirectory` instead of `sessionDao.getSessionByCode()`
  - the returned remote session shell is persisted in Room before participant join flow continues
  - update, end, and cancel operations map explicit remote results
- Added Android unit coverage in:
  - [`RemoteSessionDirectoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/sessiondirectory/RemoteSessionDirectoryTest.kt)
  - [`SessionRepositoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/SessionRepositoryTest.kt)
- Added a minimal backend under [`backend/`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/backend/) using FastAPI and SQLAlchemy with environment-driven database configuration compatible with PostgreSQL:
  - models for `users`, `sessions`, `session_members`, and `facilitator_requests`
  - explicit session status values `SCHEDULED`, `LIVE`, `ENDED`, `CANCELLED`
  - endpoints:
    - `POST /sessions`
    - `POST /sessions/resolve-code`
    - `PATCH /sessions/{sessionId}`
    - `POST /sessions/{sessionId}/end`
    - `POST /sessions/{sessionId}/cancel`
- Implemented atomic eight-digit join-code reservation via a database unique constraint plus retry-on-collision logic.
- Kept production auth and PubNub token issuance out of scope while leaving the client/API boundaries ready for them.

### Verification

- `backend/.venv/bin/pytest backend/tests`
  - Passed on Tuesday, July 28, 2026.
  - Executed `4` tests covering create/resolve, duplicate-code collision retry, missing/ended/cancelled/expired resolution, and update/end/cancel endpoint behavior.
- `./gradlew :app:assembleDebug`
  - Failed first on Tuesday, July 28, 2026, due to:
    - missing `IdUtils` import in [`SessionRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt)
    - an overly generic serialization call in [`HttpGroupAacApi`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/sessiondirectory/HttpGroupAacApi.kt)
  - Passed on Tuesday, July 28, 2026, after those focused repairs.
- `./gradlew :app:testDebugUnitTest`
  - Failed first on Tuesday, July 28, 2026, for the same compile errors blocking `assembleDebug`.
  - Passed on Tuesday, July 28, 2026, after the same repairs.

### Notes

- Backend tests currently use SQLite in-memory for determinism, while runtime configuration remains PostgreSQL-compatible through `GROUP_AAC_DATABASE_URL`.
- The default backend runtime database file `group_aac_backend.db` is ignored and is not committed.
- Production authentication, authorization, and PubNub token issuance remain deferred to later stages by design.

## Live Realtime Stage 3 - Live PubNub transport

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`

### Implemented

- Added [`PubNubSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClient.kt) backed by the existing `com.pubnub:pubnub-kotlin` dependency.
- Added one-client-per-active-UID realtime creation through [`PubNubSessionRealtimeClientFactory`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClient.kt) using the active app UID as the PubNub `UserId`.
- PubNub publish now sends canonical realtime envelopes and returns the accepted PubNub timetoken to the caller.
- PubNub subscribe now converts incoming channel messages into [`ReceivedRealtimeEvent`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/protocol/RealtimeEvent.kt) via the canonical codec, with malformed payloads surfaced as failure state instead of crashing the client.
- Added explicit realtime connection-state exposure in [`SessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionRealtimeClient.kt):
  - `Connecting`
  - `Connected`
  - `Reconnecting`
  - `Disconnected`
  - `Failed`
- `close()` on the live client now closes subscriptions, unsubscribes, disconnects, and destroys the underlying PubNub client.
- Kept all blocking PubNub initialization and teardown work off the Android main thread through the new transport factory/dispatcher path.
- Preserved [`FakeSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/FakeSessionRealtimeClient.kt) for tests and previews.
- Added [`InactiveSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/InactiveSessionRealtimeClient.kt) so configured builds do not silently fall back to the fake before a real user-specific client is activated.
- Updated [`AccountScopedRealtimeClientManager`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/RealtimeClientManager.kt) to create the next user client before closing the previous one, preventing silent loss of the active client on initialization failure.
- Updated [`AppContainer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/AppContainer.kt) to:
  - build real PubNub clients when runtime keys are configured
  - keep `PubNubTokenProvider` in the construction path for future backend-issued tokens
  - activate a persisted active UID during startup through [`RealtimeStartupInitializer`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/RealtimeStartupInitializer.kt) before active-session restoration begins
- Reordered account activation in [`AccountRepository`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/AccountRepository.kt) so realtime activation succeeds before the active-user preference is rewritten.
- Removed duplicate legacy publication paths where actions were being sent both through `PiClient` and `SessionRealtimeSync`:
  - message sends
  - display show/restore/pin/unpin/clear
  - initial host/participant join publication paths that were already represented canonically

### Verification

- `./gradlew :app:assembleDebug`
  - Failed first on Tuesday, July 28, 2026, due to new PubNub adapter compile issues:
    - generic inference on the per-channel shared flow
    - a visibility leak from an internal transport test hook
    - a Kotlin callback/property mismatch on the PubNub subscription listener
  - Passed on Tuesday, July 28, 2026, after those focused repairs.
- `./gradlew :app:testDebugUnitTest`
  - Failed first on Tuesday, July 28, 2026, for the same initial PubNub adapter compile issues.
  - Failed second on Tuesday, July 28, 2026, because [`PubNubSessionRealtimeClientTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClientTest.kt) emitted before its collector was fully active.
  - Passed on Tuesday, July 28, 2026, after the adapter fixes and the test-timing repair.
  - Final unit-test run completed `49` tests with `0` failures.

### Added tests

- [`PubNubSessionRealtimeClientTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClientTest.kt)
  - canonical event serialization passed to the PubNub adapter
  - accepted timetoken returned
  - incoming event parsing
  - malformed input handling
  - connection-state updates
- [`RealtimeClientManagerTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RealtimeClientManagerTest.kt)
  - account switch closes the old realtime client
  - sign-out/deactivation closes the active realtime client
- [`RealtimeStartupInitializerTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/RealtimeStartupInitializerTest.kt)
  - persisted UID activation on startup

### Notes

- Local `pubnub.properties` credentials were present on Tuesday, July 28, 2026.
- A PubNub Debug Console publish/receive smoke test was not executed in this noninteractive environment because it requires direct access to the PubNub web console and external service interaction beyond the local automated test path.
- `pubnub.properties` remains ignored, and no secret key was added to Android configuration.

## Live Realtime Stage 5 - Durable realtime delivery

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`
Starting commit: `216270b Add runtime session subscriptions`

### Implemented

- Split message persistence into separate concerns:
  - content lifecycle remains in [`MessageStatus`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/MessageStatus.kt)
  - transport delivery now lives in [`MessageTransportStatus`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/MessageTransportStatus.kt)
  - display delivery now lives in [`MessageDisplayStatus`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/MessageDisplayStatus.kt)
- Changed newly sent messages to begin in transport state `PENDING` instead of `SENT`, while saved-message behavior remains its own boolean flag.
- Added durable outbox domain typing through [`OutboxDomainType`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/model/OutboxDomainType.kt) and updated [`OutboxEventEntity`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/entity/OutboxEventEntity.kt) to persist:
  - domain type
  - domain id
  - transport state
  - attempts
  - retry timing
  - accepted timetoken
- Moved message, session, facilitator-request, signal, membership, and display publications onto a local-first transaction pattern:
  - repositories now write the domain mutation and outbox row in one transaction through [`TransactionRunner`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/RepositorySupport.kt)
  - publication is deferred until after commit through [`OutboxDispatcher`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcher.kt)
- Added repository support helpers:
  - [`RoomTransactionRunner`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/RepositorySupport.kt)
  - [`ImmediateTransactionRunner`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/RepositorySupport.kt)
  - [`OutboxDispatching`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcher.kt)
- Added bounded retry, stale-send recovery, and explicit retry support in [`RealtimeReliabilityStore`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStore.kt) and [`ReliabilityDao`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/dao/ReliabilityDao.kt):
  - `PENDING`, `SENDING`, `SENT`, `FAILED`
  - exponential backoff capped at 30 seconds
  - stale `SENDING` recovery after interruption
  - manual retry reset for max-attempt rows
  - monotonic channel cursor updates that never move backward
- Added application-scope immediate dispatch plus WorkManager fallback scheduling in [`OutboxDispatcher`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcher.kt), with fallback scheduling treated as best-effort when WorkManager is unavailable during tests.
- Updated [`SessionSubscriptionCoordinator`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinator.kt) to replay channel history after the stored cursor before live collection starts.
- Kept Room as the UI source of truth by projecting outgoing and delivery updates back into persisted message/display state rather than relying on ephemeral transport callbacks.

### Added tests

- [`OutboxDispatcherTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/reliability/OutboxDispatcherTest.kt)
  - atomic message plus outbox insertion
  - failure marks `FAILED`
  - stale `SENDING` recovery
  - backoff behavior
  - max-attempt cutoff
  - manual retry
  - truthful pending/failed/sent projection into message state
- [`RealtimeReliabilityStoreTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/reliability/RealtimeReliabilityStoreTest.kt)
  - processed-event deduplication
  - expired outbox exclusion
  - stale display-command rejection
  - monotonic cursor advancement
- [`DefaultSessionRealtimeSyncTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt)
  - outbox staging for outgoing message events
  - duplicate history/live event-id deduplication
- [`SessionSubscriptionCoordinatorTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/SessionSubscriptionCoordinatorTest.kt)
  - replay-after-cursor startup behavior

### Verification

- `./gradlew :app:assembleDebug`
  - Passed on Tuesday, July 28, 2026.
- `./gradlew :app:testDebugUnitTest`
  - Failed first on Tuesday, July 28, 2026, because WorkManager fallback initialization was crashing Robolectric test startup after the new fallback scheduler was introduced.
  - Failed second on Tuesday, July 28, 2026, due to two incorrect Stage 5 test assumptions:
    - auto-display adds a second outbox row for eligible group messages
    - explicit retry records one fresh send attempt before transitioning to `SENT`
  - Passed on Tuesday, July 28, 2026, after making WorkManager fallback best-effort in tests/non-initialized environments and correcting the two test expectations.
  - Final unit-test run completed `64` tests with `0` failures.

### Notes

- The live PubNub history adapter path is still a placeholder in [`PubNubSessionRealtimeClient`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/PubNubSessionRealtimeClient.kt): subscription replay is wired and unit-tested through fake/history-capable clients, but real PubNub fetch-after-cursor behavior still needs transport implementation in a later stage.
- The legacy `PiClient` compatibility boundary still exists while the rest of the session and display flows continue migrating onto the realtime/outbox model.

## Live Realtime Stage 6 - Cross-device facilitator approval

Status: complete

Date: 2026-07-28
Branch: `feature/pubnub-live-integration`
Starting commit: `6921bd3 Make realtime delivery durable`

### Implemented

- Added [`FacilitatorApprovalPayload`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/RealtimePayloads.kt) containing:
  - approved request
  - resulting `SessionMemberPayload`
  - current `SessionPayload`
- Added [`FacilitatorDeclinePayload`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/RealtimePayloads.kt) so private decline delivery stays explicit and symmetrical for requester-side handling.
- Updated [`SessionRepository.approveJoinRequest()`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt) so host approval now atomically:
  - marks the request `APPROVED`
  - creates or updates the facilitator membership
  - enqueues private `facilitator.approved` to `session.<sessionId>.<requesterUid>`
  - enqueues public `member.joined` for roster propagation
- Updated host decline handling so private `facilitator.declined` stays requester-specific and is staged in the same transaction as the request status update.
- Updated [`DefaultSessionRealtimeSync`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSync.kt) so requester-side `facilitator.approved` applies:
  - session shell
  - facilitator membership
  - approved request
  - processed event
  - channel cursor
  in one transaction before the UI can activate the requester.
- Kept requester activation in [`SessionCoordinatorViewModel`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/ui/session/SessionCoordinatorViewModel.kt) and [`SessionRepository.activateApprovedFacilitatorRequest()`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/main/java/com/example/groupaac/data/repository/SessionRepository.kt), but made that activation independent of receiving the separate public `member.joined` event first.
- Preserved the public `member.joined` event as a roster update for other clients only.
- Kept duplicate private approval delivery harmless through the existing processed-event deduplication path and idempotent upserts.

### Added tests

- [`SessionRepositoryTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/repository/SessionRepositoryTest.kt)
  - host approval publishes the private approval payload and public roster event
  - decline publishes the private decline path cleanly
- [`DefaultSessionRealtimeSyncTest`](/Users/doraqi/Desktop/Aphasia AAC/GroupAAC/GroupAacPrototype/app/src/test/java/com/example/groupaac/data/realtime/sync/DefaultSessionRealtimeSyncTest.kt)
  - self-contained host approval payload contents
  - request/approval flow across separate databases
  - atomic requester apply of session/member/request plus processed event and cursor
  - requester activation without waiting for the public roster event
  - public roster propagation through `member.joined`
  - duplicate private approval delivery
  - private decline handling

### Verification

- Focused Stage 6 verification:
  - `./gradlew :app:testDebugUnitTest --tests "com.example.groupaac.data.realtime.sync.DefaultSessionRealtimeSyncTest" --tests "com.example.groupaac.data.repository.SessionRepositoryTest"`
    - Passed on Tuesday, July 28, 2026.
- Full verification:
  - `./gradlew :app:assembleDebug :app:testDebugUnitTest`
    - Passed on Tuesday, July 28, 2026.
    - Final unit-test run completed `71` tests with `0` failures.

### Notes

- A live two-client runtime smoke test was not run during this stage because this environment did not provide two concurrently available app clients beyond the new separate-database unit coverage.
- The requester-side activation guarantee now depends on the private approval payload rather than ordering assumptions between private and public channels, which removes the prior cross-device race.
