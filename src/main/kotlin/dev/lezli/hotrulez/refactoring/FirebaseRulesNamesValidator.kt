package dev.lezli.hotrulez.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import dev.lezli.hotrulez.references.FirebaseRulesBuiltins

/**
 * Validates names entered in the Rename dialog: a new name must be a legal
 * Firestore Rules identifier and must not be a reserved keyword.
 */
class FirebaseRulesNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean = name in FirebaseRulesBuiltins.KEYWORDS

    override fun isIdentifier(name: String, project: Project?): Boolean =
        IDENTIFIER.matches(name) && name !in FirebaseRulesBuiltins.KEYWORDS

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
