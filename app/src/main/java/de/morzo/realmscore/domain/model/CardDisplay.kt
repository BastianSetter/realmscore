package de.morzo.realmscore.domain.model

import java.util.Locale

/**
 * Locale-aware card name (Phase 19). English when [locale] is English and an [CardDefinition.nameEn]
 * override exists, otherwise the German name (never an empty string).
 */
fun CardDefinition.displayName(locale: Locale): String =
    if (locale.language == "en") nameEn ?: nameDe else nameDe

/**
 * Locale-aware card rule text (Phase 19). Falls back to the German text when no English override
 * is available.
 */
fun CardDefinition.displayRuleText(locale: Locale): String =
    if (locale.language == "en") ruleTextEn ?: ruleTextDe else ruleTextDe
