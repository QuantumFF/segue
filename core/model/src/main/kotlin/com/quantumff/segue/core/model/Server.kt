package com.quantumff.segue.core.model

/** A configured Navidrome (Subsonic) server. Credentials are stored separately. */
data class Server(val id: Long, val name: String, val baseUrl: String, val username: String)
