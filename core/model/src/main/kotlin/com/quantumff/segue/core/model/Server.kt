package com.quantumff.segue.core.model

/** A configured Navidrome (Subsonic) server account. The password is stored separately, encrypted. */
data class Server(val id: String, val name: String, val baseUrl: String, val username: String)
