package com.gathin.transfer

data class FoundTv(val name: String, val host: String, val port: Int)

data class SavedTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val pin: String
)

data class RemoteFile(val name: String, val size: Long, val time: Long)

class PinRejected : Exception()
