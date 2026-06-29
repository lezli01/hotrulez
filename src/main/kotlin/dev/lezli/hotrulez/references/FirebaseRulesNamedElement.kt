package dev.lezli.hotrulez.references

import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Marker for the user-defined Firestore Rules declarations that participate in
 * symbol intelligence: function declarations, parameters, `let` bindings, and
 * path / wildcard variables.
 *
 * Each carries a single name identifier (its `IDENTIFIER` child) and is
 * renamable; identifier *uses* resolve to one of these through
 * [FirebaseRulesReference]. Wired onto the relevant rules in
 * `FirebaseRules.bnf` via the `implements` attribute, with shared behaviour in
 * [FirebaseRulesNamedElementBase].
 */
interface FirebaseRulesNamedElement : PsiNameIdentifierOwner
