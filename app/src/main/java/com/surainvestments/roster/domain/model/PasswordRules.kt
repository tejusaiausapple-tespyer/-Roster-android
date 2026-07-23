package com.surainvestments.roster.domain.model

/**
 * Mirrors iOS's `BusinessRules.passwordRules`/`passwordErrors` exactly — 8+
 * chars/uppercase/number are hard requirements, a symbol is recommended-only.
 * A small slice of the Phase 7 business-rules module, pulled forward because
 * Phase 4's change-password flow needs it now.
 */
object PasswordRules {
    data class Rule(val label: String, val isMet: Boolean, val required: Boolean)

    fun rules(password: String): List<Rule> = listOf(
        Rule("At least 8 characters", password.length >= 8, required = true),
        Rule("One uppercase letter", password.any { it.isUpperCase() }, required = true),
        Rule("One number", password.any { it.isDigit() }, required = true),
        Rule("One symbol (recommended)", password.any { !it.isLetterOrDigit() }, required = false),
    )

    fun errors(password: String): List<String> =
        rules(password).filter { it.required && !it.isMet }.map { it.label }
}
