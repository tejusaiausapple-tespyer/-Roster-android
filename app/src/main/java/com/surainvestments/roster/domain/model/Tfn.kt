package com.surainvestments.roster.domain.model

/**
 * Australian Tax File Number helpers. Mirrors iOS `TFN.swift` exactly.
 * Algorithm: 9 digits, weighted sum mod 11 == 0 (weights 1,4,3,7,5,8,6,9,10).
 */
object Tfn {
    private val weights = intArrayOf(1, 4, 3, 7, 5, 8, 6, 9, 10)

    fun normalize(raw: String): String = raw.filter { it.isDigit() }.take(9)

    fun isValid(raw: String): Boolean {
        val digits = normalize(raw)
        if (digits.length != 9) return false
        if (digits.toSet().size == 1) return false
        var sum = 0
        for (i in digits.indices) {
            val d = digits[i].digitToIntOrNull() ?: return false
            sum += d * weights[i]
        }
        return sum % 11 == 0
    }

    /** XXX XXX XXX */
    fun format(raw: String): String {
        val d = normalize(raw)
        val parts = mutableListOf<String>()
        if (d.isNotEmpty()) parts += d.take(3)
        if (d.length > 3) parts += d.substring(3, minOf(6, d.length))
        if (d.length > 6) parts += d.substring(6, minOf(9, d.length))
        return parts.joinToString(" ")
    }

    /** *** *** 123 */
    fun mask(raw: String?): String {
        val d = normalize(raw ?: "")
        if (d.length < 3) return if (d.isEmpty()) "—" else "•••"
        return "*** *** ${d.takeLast(3)}"
    }

    fun last4(raw: String?): String {
        val d = normalize(raw ?: "")
        return if (d.length < 4) "" else d.takeLast(4)
    }

    /** Empty allowed; non-empty must be valid. */
    fun validationError(raw: String): String? {
        val d = normalize(raw)
        if (d.isEmpty()) return null
        if (d.length != 9) return "TFN must be 9 digits"
        if (!isValid(d)) return "Enter a valid Australian TFN"
        return null
    }
}
