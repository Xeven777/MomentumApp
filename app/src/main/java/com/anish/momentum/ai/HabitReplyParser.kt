package com.anish.momentum.ai

import com.anish.momentum.utils.AiHabit

/**
 * Turns a free-form model reply into habit rows. Models add bullets, numbering
 * and emphasis even when told not to, so those are stripped before splitting
 * the leading emoji from the habit name.
 */
object HabitReplyParser {

    private val leadingMarker = Regex("^[-*•]\\s*")
    private val leadingNumber = Regex("^\\d+[.)]\\s*")

    /** Guards against prose ("Here are 5 habits:") being read as a habit row. */
    private const val MAX_NAME_LENGTH = 60

    fun parse(reply: String): List<AiHabit> =
        reply.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(leadingMarker, "") }
            .map { it.replace(leadingNumber, "") }
            .map { it.trim().trim('*', '`', '"') }
            .mapNotNull { line ->
                val emoji = line.takeWhile { !it.isWhitespace() }
                val name = line.dropWhile { !it.isWhitespace() }.trim()
                if (emoji.isEmojiLike() && name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
                    AiHabit(emoji, name)
                } else {
                    null
                }
            }

    /** A real emoji or symbol, not an ordinary word from a sentence. */
    private fun String.isEmojiLike(): Boolean =
        isNotEmpty() && any { it.code > 0x7F }
}
