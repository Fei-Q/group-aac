# Group AAC — Current App State and Implementation Roadmap

**Updated:** 2026-07-29  
**Repository:** `Fei-Q/group-aac`  
**Working branch:** `feature/pubnub-pi-prototype-hardening`

1. Current Architecture

The prototype no longer depends on a custom FastAPI/Python backend.

Facilitator Android app ─┐
Participant Android apps ├── PubNub ── Raspberry Pi display client
Room on each Android ────┘

Android

Android stores local users, settings, sessions, memberships, messages, drafts, signals, display state, reliability state, and local attachment metadata in Room.

The Android app:

creates sessions locally
binds sessions to a Raspberry Pi display
registers short join codes through PubNub App Context
exchanges session events through PubNub
manages participant and facilitator membership
controls the shared display
Raspberry Pi

The production Raspberry Pi software is being written separately in C++ by the professor.

The Python package under pi/ is only:

a protocol reference implementation
a local simulator for Android integration
an automated state-machine test harness
a fixture source for validating the C++ implementation

It is not a backend and is not intended to be the production Pi application.

2. Completed Work
Stage 1 — Backend Removal and Local Session Creation

Completed:

Removed the active custom backend architecture.
Removed backend-specific Android clients, URLs, and configuration.
Preserved SessionDirectory as a discovery abstraction.
Added SessionStatus.DRAFT.
Added display ID and expiry state to sessions.
Immediate sessions are created locally with host membership.
Scheduled sessions are created locally.
New sessions are not joinable before display launch.
Draft session creation no longer activates ActiveSessionStore.

Current lifecycle:

create local session
→ DRAFT or SCHEDULED
→ no directory registration
→ no active in-session shell
Stage 2 — PubNub Short-Code Directory

Completed:

Added PubNub App Context–based short-code lookup.
Metadata IDs use:
join.<eight digits>
Added register, resolve, update, and removal result types.
Added explicit handling for invalid, missing, expired, terminal, unsupported, and failed directory lookups.
Number-code lookup no longer requires the removed backend.
Added directory and metadata transport tests.

Audit item:

Confirm App Context metadata operations use a properly managed PubNub transport/client lifecycle rather than an unnecessary unmanaged second client.
Stage 3A — Display Protocol

Completed:

Added:
display.<displayId>.control
display.<displayId>.events
Added display pairing payloads.
Added versioned session invitation payloads.
Added bind, bound, bind-failed, unbind, and unbound event types.
Added display-device event routing.
Updated subscription-scope handling.
Added protocol and router tests.
Stage 3B — Android Display Binding Coordinator

Completed:

Added DisplayBindingCoordinator.
Android subscribes for a correlated reply before publishing bind or unbind.
Added timeout, expiry, rejection, and failure results.
Added SessionRepository.launchSessionOnDisplay().

Current launch sequence:

validate host and session
→ verify DRAFT or SCHEDULED
→ select an available short code
→ construct provisional LIVE invitation
→ bind the display
→ register directory entry
→ commit LIVE session locally
→ publish session.started
→ publish host membership
→ activate host session
Added rollback through best-effort directory removal and display unbind.
Corrected conversion from SessionParticipantRow to SessionMemberEntity.
Added binding coordinator tests.
Stage 3C — Python Pi Protocol Simulator

Completed:

Added a Python Pi state-machine simulator.
Added stable display identity.
Added short-lived pairing nonce and expiry.
Added pairing JSON and QR generation.
Added persisted binding recovery.
Added duplicate-command idempotency.
Added already-bound and wrong-display rejection.
Added unbind and restart restoration.
Added PubNub device control/event behavior.
Python Pi test suite passes: 12 tests.

The professor’s C++ client should eventually replace the Python runtime while preserving the same protocol.

Stage 3D — Facilitator Display-Launch UI

Completed:

Added Google Code Scanner integration for display pairing QRs.
Added display-launch state to SessionCoordinatorViewModel.
Added scan, connecting, retry, cancel, timeout, expiry, rejection, and failure UI.
Removed direct UI calls to startScheduledSession().
Draft creation no longer opens the facilitator session shell.
The app enters the in-session facilitator shell only after LaunchSessionResult.Launched.
Updated lifecycle and realtime-event coverage tests.
Made AdvancedHomeScreen tests deterministic using an injected date.
Android unit tests pass after Stage 3 regressions were repaired.
3. Current Expected Flow
facilitator creates or selects a DRAFT/SCHEDULED session
→ display connection dialog opens
→ facilitator scans Pi pairing QR
→ Android decodes DisplayPairingPayload
→ Android publishes display.bind_session
→ Python simulator or compatible C++ Pi publishes display.bound
→ Android registers join.<code>
→ Android marks session LIVE
→ ActiveSessionStore is activated
→ facilitator in-session UI opens

Not yet complete:

Pi displays participant session QR and number code
→ participant scans group-aac-session QR
→ participant sees session preview
→ participant confirms join
→ QR and number-code paths share one join pipeline
4. Current Verification Status

Confirmed:

Android unit tests pass.
Python Pi state-machine tests pass.
Display router tests pass.
Realtime event coverage tests pass.
Draft creation lifecycle tests pass.
Home calendar tests are deterministic.

Still verify and record:

./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest --stacktrace

Still requiring live verification:

Android ↔ PubNub ↔ Pi-simulator binding
two simultaneous Android accounts/devices
participant-to-facilitator realtime exchange
compatibility with the professor’s C++ Pi client

Known non-blocking warning:

Modifier.menuAnchor() is deprecated.

Migrate it when editing the surrounding UI. Do not prioritize it over protocol work.

5. Immediate Risks
Draft Versus Live Lifecycle

No path may make a session joinable or activate the host before both display binding and directory registration succeed.

Pi Activation Ordering

The Pi should not show the participant join screen merely because it accepted display.bind_session.

It should:

validate and persist the binding
subscribe to the session channels
publish display.bound
display the participant join screen only once launch state is coherent
Protocol Drift

Android, Python fixtures, documentation, and the C++ implementation must agree on:

channel names
event names
field names
protocol versions
timestamp units
join-code formatting
acknowledgement correlation
idempotency behavior
Coroutine Cancellation

Suspend paths may still convert cancellation into ordinary failure. CancellationException must be rethrown.

PubNub History

Production history retrieval remains incomplete. A hardcoded empty result prevents reconnect and offline recovery.

Subscription Ownership

Session, requester, and display subscriptions are not yet fully disposable. Old channels may continue delivering after session/account transitions.

Display Command Correctness

Later display commands still require:

one canonical command event ID
exact acknowledgement correlation
stale acknowledgement rejection
correct pin semantics
separate local and PubNub timestamps
6. Next Stages
Stage 4A — Shared Invitation Join Pipeline

Create one internal repository path:

suspend fun joinInvitation(
    invitation: SessionInvitationPayload,
    userId: String,
    displayName: String,
    requestedRole: SessionRole
): JoinSessionResult

Requirements:

validate invitation type
validate protocol version
validate LIVE status
validate expiry
validate session, display, and host IDs
normalize and validate the join code
QR joining must not call SessionDirectory
number-code joining resolves a directory entry and converts it to the same invitation
both paths persist equivalent local state
preserve facilitator approval behavior
preserve coroutine cancellation
Stage 4B — Participant QR Scanner and Preview

Extend the existing JoinSessionScreen.

Add:

participant session QR scanning
resolving state
session preview
malformed, expired, not-found, and failure states
explicit confirmation before joining
shared QR/code confirmation path
phone and tablet support
stable test tags and accessibility descriptions

Do not:

show success before lookup
auto-join immediately after scanning
run directory lookup with fewer than eight digits
reuse the display-pairing decoder as though it were a session invitation decoder
Stage 4C — C++ Pi Protocol Handoff

Create:

docs/pi-display-protocol-contract.md
docs/pi-display-protocol-fixtures.json
docs/pi-cpp-integration-checklist.md

Include:

pairing QR
session invitation QR
bind and unbind commands
bound, bind-failed, and unbound replies
channel matrix
timestamp units
state transitions
idempotency
restart behavior
one-active-session rule
acknowledgement correlation
join-code formatting

Kotlin and Python tests should load the same fixture file.

Stage 5 — Startup and Cancellation Safety
remove startup runBlocking
add asynchronous startup state
restore account, PubNub client, session, subscriptions, display binding, and directory state in order
prevent automatic duplicate Pi binding on restart
propagate coroutine cancellation
Stage 6 — PubNub History Recovery

Implement real Message Persistence retrieval:

cursor-aware
paginated
ascending by timetoken
cancellation-safe
malformed-event isolation
session, facilitator, private-user, display, and retained device-event coverage
Stage 7 — Disposable and Race-Free Subscriptions

Introduce explicit subscription ownership.

Required sequence:

establish live subscription
→ buffer live events
→ read cursor
→ fetch history
→ apply history
→ merge buffered events
→ deduplicate
→ continue live

Close old subscriptions on:

leave
end
cancel
account switch
sign-out
facilitator approval
active-session replacement
display bind/unbind/rebind
Stage 8 — Display Command and Pi-State Correctness
generate each display command once
store and publish the same event ID
verify inReplyToEventId
reject stale and unrelated acknowledgements
preserve display state on mode change
implement correct pin/manual/automatic replacement behavior
keep epoch milliseconds separate from PubNub timetokens
Stage 9 — Account-Scoped Durable Outbox
add publishing-user ownership
add atomic claim and lease
add delayed WorkManager retry
prevent cross-account publication
reconcile directory and display-binding operations durably
Stage 10 — Shared Versus Local Message State
remove local-only fields from wire payloads
add per-user saved/hidden state
add explicit audience and recipient scope
enforce viewer-aware private message queries
Stage 11 — Signal and Snooze Lifecycle
replace prior current signals transactionally
add expiry-aware snoozes
add synchronized unsnooze
preserve facilitator-specific snooze state
Stage 12 — Restart-Safe Approvals and Snapshots
restore pending facilitator requests from Room
make host-only snapshot responses explicit
prevent old snapshots from overwriting newer state
reconcile host, directory, and Pi state after restart
Stage 13 — PubNub Files Attachments
add upload/download abstraction
never send local URIs
add failure and retry state
define supported Pi MIME types
add cache cleanup
Stage 14 — Full Vertical Slice
host creates session
→ binds C++ Pi
→ Pi shows QR/code
→ participant joins by QR
→ participant joins by number
→ messages and signals synchronize
→ host controls display
→ Pi acknowledges
→ restart and reconnect recovery work
→ attachments transfer
→ host ends session
→ directory closes
→ Pi returns to idle
7. Development Rules

For each stage:

Inspect the current implementation first.
Preserve existing APIs unless replacement is intentional.
Add focused tests with the implementation.
Run focused tests.
Run the complete Android unit suite.
Run assembleDebug.
Update status documentation.
Commit only when passing.
Stop rather than disabling or weakening tests.
Never add credentials.
Do not merge to main.
Do not treat the Python simulator as the production Pi application.
8. Recommended Commit Sequence
1. Document and freeze display pairing milestone
2. Unify participant invitation join pipeline
3. Add participant QR preview and joining
4. Add C++ Pi protocol fixtures and handoff docs
5. Make startup and cancellation safe
6. Implement PubNub history recovery
7. Make PubNub subscriptions race-free
8. Correct display command and Pi state semantics
9. Harden account-scoped realtime delivery
10. Separate shared and local message state
11. Complete signal replacement and snooze lifecycle
12. Make approvals and snapshots restart-safe
13. Add PubNub attachment transfer
14. Verify the complete Android and C++ Pi vertical slice

Save the second block as:

```text
group_aac_codex_overnight_prompt_queue.md
# Codex Overnight Prompt Queue — Group AAC

Run these prompts sequentially on:

```text
feature/pubnub-pi-prototype-hardening

Every prompt must begin with:

git status --short
git log -1 --oneline

If the previous prompt did not leave a clean, passing commit, stop and report the blocker. Do not layer a new stage onto a broken tree.
