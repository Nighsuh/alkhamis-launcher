package com.gathin.transfer

/** A failed send is tried again up to three times. An unfinished file is sent whole again. */
object Queue {
    const val MAX = 3

    fun nextAttempt(attempts: Int): Int? {
        val next = attempts + 1
        return if (next >= MAX) null else next
    }
}

data class Job(
    val id: String,
    val host: String,
    val port: Int,
    val pin: String,
    val name: String,
    val uri: String?,
    val text: String?,
    val attempts: Int
)
