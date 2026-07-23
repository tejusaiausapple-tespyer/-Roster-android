package com.surainvestments.roster.domain.model

import com.google.firebase.Timestamp

/**
 * Tolerant Firestore field readers shared by the domain models below — the Kotlin
 * analogue of iOS's `FS` helper (`Models/FirestoreValue.swift`). Firestore's Android
 * SDK hands back a raw `Map<String, Any?>` from `DocumentSnapshot.data`, where numbers
 * may arrive as `Long` or `Double` and instants as [Timestamp]; these coerce both
 * tolerantly instead of crashing on a type mismatch.
 */

internal fun Map<String, Any?>.fsString(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.fsDouble(key: String): Double = (this[key] as? Number)?.toDouble() ?: 0.0

internal fun Map<String, Any?>.fsInt(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

internal fun Map<String, Any?>.fsBoolean(key: String): Boolean = this[key] as? Boolean ?: false

internal fun Map<String, Any?>.fsInstant(key: String): java.time.Instant? =
    (this[key] as? Timestamp)?.toDate()?.toInstant()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.fsStringList(key: String): List<String>? =
    (this[key] as? List<*>)?.filterIsInstance<String>()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.fsIntList(key: String): List<Int>? =
    (this[key] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
