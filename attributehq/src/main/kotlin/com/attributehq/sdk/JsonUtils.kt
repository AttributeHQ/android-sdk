package com.attributehq.sdk

import org.json.JSONArray
import org.json.JSONObject

/** Convert a Map to JSONObject, recursively handling nested maps and lists. */
internal fun Map<String, Any?>.toJSONObject(): JSONObject = JSONObject().also { json ->
    for ((key, value) in this) {
        json.put(key, value.toJsonValue())
    }
}

/** Convert a List to JSONArray, recursively handling nested maps and lists. */
internal fun List<Any?>.toJSONArray(): JSONArray = JSONArray().also { arr ->
    for (value in this) {
        arr.put(value.toJsonValue())
    }
}

/** Convert a JSONObject to a Map. */
internal fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = this.get(key).fromJsonValue()
    }
    return map
}

/** Convert a JSONArray to a List. */
internal fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        list.add(this.get(i).fromJsonValue())
    }
    return list
}

/** Convert an Any? value to a JSON-safe value. */
private fun Any?.toJsonValue(): Any = when (this) {
    null -> JSONObject.NULL
    is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        (this as Map<String, Any?>).toJSONObject()
    }
    is List<*> -> this.toJSONArray()
    is Boolean, is Int, is Long, is Float, is Double, is String -> this
    is Number -> this.toDouble()
    else -> this.toString()
}

/** Convert a JSON value back to Kotlin types. */
private fun Any.fromJsonValue(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> this.toMap()
    is JSONArray -> this.toList()
    else -> this
}
