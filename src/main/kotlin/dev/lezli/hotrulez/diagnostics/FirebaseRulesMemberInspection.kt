package dev.lezli.hotrulez.diagnostics

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.EditDistance
import dev.lezli.hotrulez.diagnostics.fixes.RenameMemberFix
import dev.lezli.hotrulez.diagnostics.fixes.asQuickFix
import dev.lezli.hotrulez.psi.FirebaseRulesCallExpression
import dev.lezli.hotrulez.psi.FirebaseRulesExpression
import dev.lezli.hotrulez.psi.FirebaseRulesMemberExpression
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.psi.FirebaseRulesVisitor
import dev.lezli.hotrulez.references.FirebaseRulesMemberPath
import dev.lezli.hotrulez.references.FirebaseRulesScopes
import dev.lezli.hotrulez.references.RulesService

/**
 * Flags a member access `<receiver>.<member>` when the receiver is a *closed*,
 * service-scoped built-in and `member` is not part of that receiver's fixed,
 * doc-sourced set (`RulesService.closedReceivers`). This is the plugin's first
 * **semantic** check — it extends the `FirebaseRulesSymbolInspection` philosophy
 * (resolve against a fixed model, never invent) from undefined *references* to
 * unknown *members* — while inferring no types and judging no authorization.
 *
 * It is deliberately conservative (a false positive on valid, idiomatic rules is
 * worse than a missed true positive):
 *
 *  - it flags ONLY against [RulesService.closedReceivers] (the flag authority), never
 *    every [RulesService.members] key — the two diverge on purpose;
 *  - it is service-scoped: cross-dialect accesses (`resource.size` in a Firestore file,
 *    `request.method` in a Storage file) are true positives *because* the closed sets
 *    are per dialect, so it resolves [RulesService.forElement] and never merges dialects;
 *  - **open namespaces are never flagged** — `request.auth.token.*` (incl. `.firebase.*`),
 *    `*.data.*`, Storage `*.metadata.*` and `request.params.*` — by construction, since
 *    they are absent from `closedReceivers`, so the single closed-key lookup below already
 *    skips them (the invariant is pinned by `FirebaseRulesClosedReceiverDriftTest`);
 *  - a user symbol shadowing the built-in root (a `let`/param/path-var named
 *    `request`/`resource`) is skipped, so its members are never flagged;
 *  - a receiver that is not a pure reference/member chain (a call / index / paren result)
 *    is skipped, so `resource().foo` / `resource[0].foo` are never flagged;
 *  - a member in call position (a method call like `request.auth.get(...)`) is skipped:
 *    the closed sets document *properties*, and `request.auth` is itself a `rules.Map`
 *    whose methods cannot be enumerated without type inference.
 *
 * Neutral files (no recognised `service`) flag nothing. Severity is `WEAK WARNING`
 * (see `plugin.xml`), and the wording stays structural — "'x' is not a member of
 * `request`", never "insecure" / "authorizes".
 */
class FirebaseRulesMemberInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : FirebaseRulesVisitor() {
            override fun visitMemberExpression(member: FirebaseRulesMemberExpression) {
                // Don't pile onto the parser's own diagnostics while the file is malformed.
                if (PsiTreeUtil.getParentOfType(member, PsiErrorElement::class.java) != null) return
                check(member, holder)
            }
        }

    private fun check(member: FirebaseRulesMemberExpression, holder: ProblemsHolder) {
        // Judge only PROPERTY access. A member in call position (`request.auth.get(...)`) is a
        // method call, and whether a method exists on a receiver needs type knowledge the plugin
        // does not have (no type inference) — `request.auth`, notably, is a `rules.Map` with
        // documented map methods (get/keys/values/size/diff). The closed sets are documented
        // *property* interfaces, so method calls on them are never flagged.
        if (member.isCallee()) return

        val dialect = RulesService.forElement(member) ?: return // neutral file: flag nothing

        // Only a pure `member_expression* -> reference_expression` receiver denotes a real
        // built-in member path; a call / index / paren result that happens to collapse to a
        // closed key (`resource().foo`, `resource[0].foo`) must not be flagged.
        val root = chainRoot(member) ?: return

        val receiverKey = FirebaseRulesMemberPath.receiverKey(member.expression)
        val closed = dialect.closedReceivers[receiverKey] ?: return // not a closed receiver: silent

        val name = member.identifier.text
        if (name.isEmpty() || name in closed) return // valid member (or, defensively, a blank identifier): silent

        // A `let`/param/path-var named `request`/`resource` shadows the built-in — its members
        // are the user's, not ours to judge.
        if (FirebaseRulesScopes.resolveVariable(root, root.identifier.text) != null) return

        val (message, fixes) = describe(dialect, receiverKey, name, closed, member)
        holder.registerProblem(member.identifier, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)
    }

    /**
     * The root reference of a pure member chain, or null when the receiver contains a
     * call / index / paren / literal anywhere (so the textual
     * [FirebaseRulesMemberPath.receiverKey] would not faithfully denote a built-in path).
     */
    private fun chainRoot(member: FirebaseRulesMemberExpression): FirebaseRulesReferenceExpression? {
        var expression: FirebaseRulesExpression = member.expression
        while (expression is FirebaseRulesMemberExpression) expression = expression.expression
        return expression as? FirebaseRulesReferenceExpression
    }

    /** True when [this] member is the callee of a call, i.e. a method call `receiver.member(...)`. */
    private fun FirebaseRulesMemberExpression.isCallee(): Boolean {
        val parent = parent
        return parent is FirebaseRulesCallExpression && parent.expression === this
    }

    /**
     * The message and quick-fixes for an unknown [member] on the closed [receiverKey]:
     *  1. a near in-dialect member (Levenshtein ≤ [MAX_TYPO_DISTANCE]) → a terse structural
     *     message plus one "Change to 'X'" rename fix per near match, closest first;
     *  2. else a member valid on the *other* dialect's same receiver → a dialect-aware
     *     message (no fix), e.g. `resource.size` in Firestore or `request.method` in Storage;
     *  3. else a plain unknown → a structural message enumerating the receiver's valid set.
     */
    private fun describe(
        dialect: RulesService,
        receiverKey: String,
        member: String,
        closed: Set<String>,
        element: FirebaseRulesMemberExpression,
    ): Pair<String, Array<LocalQuickFix>> {
        val nearMatches = closed
            .map { it to EditDistance.levenshtein(it, member, /* caseSensitive = */ true) }
            .filter { it.second <= MAX_TYPO_DISTANCE }
            .sortedBy { it.second }
            .map { it.first }
        if (nearMatches.isNotEmpty()) {
            val fixes = nearMatches.map { RenameMemberFix(element, it).asQuickFix() }.toTypedArray()
            return "'$member' is not a member of `$receiverKey`." to fixes
        }

        val other = RulesService.entries.first { it != dialect }
        if (member in other.closedReceivers[receiverKey].orEmpty()) {
            val message = "'$member' is a ${other.label} member; ${dialect.label} `$receiverKey` " +
                "exposes {${closed.joinToString(", ")}}."
            return message to LocalQuickFix.EMPTY_ARRAY
        }

        return "'$member' is not a member of `$receiverKey` (${closed.joinToString(", ")})." to
            LocalQuickFix.EMPTY_ARRAY
    }

    private companion object {
        /** Max edit distance for a "did you mean 'X'?" rename suggestion (see the spec). */
        const val MAX_TYPO_DISTANCE = 2
    }
}
