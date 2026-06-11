package com.lightchat.server.store

import org.json.JSONObject

interface StatePersistence {
    fun load(): JSONObject?
    fun save(root: JSONObject)
    fun describe(): String
}
