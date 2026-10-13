package ro.amadya.procurement.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ro.amadya.contract.model.ProductNameRule
import ro.amadya.contract.model.ProductNameRuleRequest
import ro.amadya.contract.model.ProductNameTestResult
import ro.amadya.shared.NotFoundException
import ro.amadya.shared.UnprocessableException
import java.text.Normalizer
import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import ro.amadya.contract.model.ProductNameMatchType as MatchTypeDto

/** Result of normalising one printed product name. */
data class NamedLine(val name: String, val ruleId: UUID?, val stockItemId: UUID?)

/**
 * Keyword rules that turn a printed name ("LAPTE ZUZU 3,7% GRĂSIME") into our name ("Lapte").
 * Pure matching lives in the companion so it can be tested without Spring.
 */
@Service
@Transactional(readOnly = true)
class ProductNameRules(private val rules: ProductNameRuleRepository) {

    fun list(): List<ProductNameRule> = rules.findAllByOrderByPriorityAscCreatedAtAsc().map { it.toDto() }

    @Transactional
    fun create(req: ProductNameRuleRequest): ProductNameRule {
        val type = NameMatchType.valueOf(req.matchType.value)
        validate(type, req.pattern)
        return rules.save(
            ProductNameRuleEntity(type, req.pattern.trim(), req.targetName.trim(), req.stockItemId, req.priority ?: 100, req.active ?: true),
        ).toDto()
    }

    @Transactional
    fun update(id: UUID, req: ProductNameRuleRequest): ProductNameRule {
        val rule = rules.findByIdOrNull(id) ?: throw NotFoundException("Name rule")
        val type = NameMatchType.valueOf(req.matchType.value)
        validate(type, req.pattern)
        rule.matchType = type
        rule.pattern = req.pattern.trim()
        rule.targetName = req.targetName.trim()
        rule.stockItemId = req.stockItemId
        rule.priority = req.priority ?: rule.priority
        rule.active = req.active ?: rule.active
        return rule.toDto()
    }

    @Transactional
    fun delete(id: UUID) {
        val rule = rules.findByIdOrNull(id) ?: throw NotFoundException("Name rule")
        rules.delete(rule)
    }

    fun test(text: String): ProductNameTestResult = apply(text).let { ProductNameTestResult(it.name, it.ruleId, it.stockItemId) }

    /** Applies the active rules in order; without a match the printed text is kept, in sentence case. */
    fun apply(printed: String): NamedLine = match(rules.findAllByActiveTrueOrderByPriorityAscCreatedAtAsc().map { it.toRule() }, printed)

    /** Loads the active rules once, for a whole document. */
    fun matcher(): (String) -> NamedLine {
        val active = rules.findAllByActiveTrueOrderByPriorityAscCreatedAtAsc().map { it.toRule() }
        return { match(active, it) }
    }

    private fun validate(type: NameMatchType, pattern: String) {
        if (pattern.isBlank()) throw UnprocessableException("procurement.invalid_regex", pattern)
        if (type == NameMatchType.REGEX) {
            try {
                Pattern.compile(pattern)
            } catch (e: PatternSyntaxException) {
                throw UnprocessableException("procurement.invalid_regex", e.description)
            }
        }
    }

    private fun ProductNameRuleEntity.toRule() = Rule(id, matchType, pattern, targetName, stockItemId)

    private fun ProductNameRuleEntity.toDto() =
        ProductNameRule(id, MatchTypeDto.forValue(matchType.name), pattern, targetName, priority, active, stockItemId)

    data class Rule(val id: UUID, val type: NameMatchType, val pattern: String, val target: String, val stockItemId: UUID?)

    companion object {
        private const val REGEX_BUDGET_NANOS = 50_000_000L

        fun match(rules: List<Rule>, printed: String): NamedLine {
            val text = printed.trim()
            val folded = fold(text)
            for (rule in rules) {
                if (matches(rule, text, folded)) return NamedLine(rule.target, rule.id, rule.stockItemId)
            }
            return NamedLine(sentenceCase(text), null, null)
        }

        fun matches(rule: Rule, text: String, folded: String): Boolean = when (rule.type) {
            NameMatchType.CONTAINS -> words(rule.pattern).any { w -> Regex("(?<![\\p{L}\\p{N}])${Regex.escape(w)}(?![\\p{L}\\p{N}])").containsMatchIn(folded) }
            NameMatchType.STARTS_WITH -> words(rule.pattern).any { folded.startsWith(it) }
            NameMatchType.REGEX -> regexMatches(rule.pattern, text) || regexMatches(rule.pattern, folded)
        }

        /** Upper case without diacritics: "Grăsime" → "GRASIME". */
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase().replace(Regex("\\s+"), " ")

        fun sentenceCase(s: String): String = s.lowercase().replaceFirstChar { it.titlecase() }

        private fun words(pattern: String) = pattern.split('|').map { fold(it.trim()) }.filter { it.isNotEmpty() }

        /** A runaway pattern is stopped after 50 ms and counts as no match. */
        private fun regexMatches(pattern: String, text: String): Boolean = try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).matcher(Deadline(text, System.nanoTime() + REGEX_BUDGET_NANOS)).find()
        } catch (_: PatternSyntaxException) {
            false
        } catch (_: RegexTimeout) {
            false
        }
    }

    private class RegexTimeout : RuntimeException(null, null, false, false)

    private class Deadline(private val text: CharSequence, private val deadline: Long) : CharSequence {
        override val length get() = text.length
        override fun get(index: Int): Char {
            if (System.nanoTime() > deadline) throw RegexTimeout()
            return text[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Deadline(text.subSequence(startIndex, endIndex), deadline)
        override fun toString() = text.toString()
    }
}
