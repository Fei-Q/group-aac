package com.example.groupaac.model

enum class SignalType(
    val label: String,
    val buttonLabel: String,
    val shortLabel: String,
    val priority: Int,
    val emoji: String
) {
    // Card 1: opinion / stance
    YES_AGREE(
        label = "Yes / I Agree",
        buttonLabel = "Yes",
        shortLabel = "Yes",
        priority = 4,
        emoji = "👍"
    ),


    NO_DISAGREE(
        label = "No / I Disagree",
        buttonLabel = "No",
        shortLabel = "No",
        priority = 4,
        emoji = "👎"
    ),

    OKAY(
        label = "Okay",
        buttonLabel = "OK",
        shortLabel = "OK",
        priority = 4,
        emoji = "👌"
    ),

    // Card 2: turn management
    WANT_TO_SHARE(
        label = "I want to share",
        buttonLabel = "I Want to Share",
        shortLabel = "Share",
        priority = 1,
        emoji = "🙌"
    ),

    READY(
        label = "I'm ready to share",
        buttonLabel = "Ready",
        shortLabel = "Ready",
        priority = 2,
        emoji = "✅"
    ),

    WAIT(
        label = "Please wait",
        buttonLabel = "Wait",
        shortLabel = "Wait",
        priority = 2,
        emoji = "⏳"
    ),

    COMMENT(
        label = "I have a comment",
        buttonLabel = "Comment",
        shortLabel = "Comment",
        priority = 3,
        emoji = "💬"
    ),

    QUESTION(
        label = "I have a question",
        buttonLabel = "Question",
        shortLabel = "Question",
        priority = 3,
        emoji = "❓"
    ),

    ANSWER(
        label = "I have an answer",
        buttonLabel = "Answer",
        shortLabel = "Answer",
        priority = 3,
        emoji = "✋"
    ),

    // Card 3: assistance
    HELP(
        label = "I need help",
        buttonLabel = "I Need Help",
        shortLabel = "Help",
        priority = 0,
        emoji = "🆘"
    ),

    REPEAT(
        label = "Please repeat",
        buttonLabel = "Repeat",
        shortLabel = "Repeat",
        priority = 1,
        emoji = "🔁"
    ),

    FIND_WORD(
        label = "Help me find a word",
        buttonLabel = "Find Word",
        shortLabel = "Find word",
        priority = 1,
        emoji = "🔍"
    ),

    SPELL_WORD(
        label = "Help me spell a word",
        buttonLabel = "Spell Word",
        shortLabel = "Spell",
        priority = 1,
        emoji = "🆎"
    ),

    SAY_WORD(
        label = "Help me say a word",
        buttonLabel = "Say Word",
        shortLabel = "Say word",
        priority = 1,
        emoji = "🗣️"
    ),

    // Legacy values kept so older local database rows do not crash enum conversion.
    @Deprecated("Use WANT_TO_SHARE instead.")
    HOLD_MY_TURN(
        label = "I want to share",
        buttonLabel = "I Want to Share",
        shortLabel = "Share",
        priority = 1,
        emoji = "✋"
    ),

    @Deprecated("Use YES_AGREE instead.")
    YES(
        label = "Yes / Agree",
        buttonLabel = "Yes / Agree",
        shortLabel = "Yes",
        priority = 4,
        emoji = "✅"
    ),

    @Deprecated("Use NO_DISAGREE instead.")
    NO(
        label = "No / Disagree",
        buttonLabel = "No / Disagree",
        shortLabel = "No",
        priority = 4,
        emoji = "❌"
    ),

    @Deprecated("Use WAIT instead.")
    MORE_TIME(
        label = "Please wait",
        buttonLabel = "Wait",
        shortLabel = "Wait",
        priority = 2,
        emoji = "⏳"
    );

    companion object {
        val stanceSignals = listOf(
            YES_AGREE,
            NO_DISAGREE,
            OKAY
        )

        val turnManagementSignals = listOf(
            READY,
            WAIT,
            COMMENT,
            QUESTION,
            ANSWER
        )

        val assistanceSignals = listOf(
            REPEAT,
            FIND_WORD,
            SPELL_WORD,
            SAY_WORD
        )

        val primarySignals = listOf(
            YES_AGREE,
            NO_DISAGREE,
            OKAY,
            WANT_TO_SHARE,
            READY,
            WAIT,
            COMMENT,
            QUESTION,
            ANSWER,
            HELP,
            REPEAT,
            FIND_WORD,
            SPELL_WORD,
            SAY_WORD
        )
    }
}