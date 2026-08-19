# Group AAC

**Group AAC** is a native Android augmentative and alternative communication (AAC) application designed to support people with aphasia during group conversations.

Aphasia can affect speaking, listening, reading, and writing while leaving a person's ideas and desire to participate intact. Group meetings provide valuable opportunities for people with aphasia to practice communication, maintain social connections, and participate in meaningful activities. However, our observational research found that these settings also introduce communication barriers that conventional AAC systems do not adequately address.

In particular, participants may need additional time to compose a response while the conversation continues, making it difficult to claim or preserve a turn. Non-verbal participants may also need to signal that they want to contribute before they can begin expressing their message. Personal phones and AAC devices are frequently passed around the table to share content, which slows group communication, increases facilitator workload, and can expose information the device owner did not intend to share. Facilitators must simultaneously monitor these communication needs, manage activities, and coordinate support across multiple participants.

Group AAC was designed around these interaction-level challenges.

## What the App Does

* **Private multimodal message composition:** Participants can prepare messages using text and images on their own devices, while remaining free to communicate through speech, gesture, writing, or existing AAC systems.
* **Quick conversational signals:** One-tap signals such as *ready*, *waiting*, *help*, *agree*, and *disagree* allow participants to indicate intent or request support before composing a complete message.
* **Shared group display:** Selected messages can be sent to a Raspberry Pi-powered display so everyone can view the same information simultaneously, eliminating serial device passing and supporting collaborative clarification.
* **Participant-controlled sharing:** Users decide what information is shared and when, preserving privacy and supporting activities that require secrecy.
* **Facilitator tools:** Facilitators can monitor participant signals, review messages, manage shared-display content, and coordinate session activity without relying entirely on subtle room-level cues.
* **Realtime synchronization:** Participant, facilitator, and display state is synchronized across devices through PubNub.

## Architecture

```text
Participant Android Apps ─┐
                          ├── PubNub ── Raspberry Pi Display
Facilitator Android App ──┘
```

The Android application uses a local-first architecture with Room for persistent state and PubNub for realtime communication.

**Tech stack:** Kotlin · Jetpack Compose · Room · DataStore · Hilt · PubNub · WorkManager · Kotlin Coroutines · Raspberry Pi · Python

A detailed illustration of the interface and usage workflow can be found [here](figures.pdf).

## Research

This system was developed as part of our research on AAC design for aphasia group meetings.

**“Designing AACs to Support Communication in Aphasia Group Meetings” was accepted as a Poster & Demo paper at the 28th ACM SIGACCESS Conference on Computers and Accessibility (ASSETS 2026).**
