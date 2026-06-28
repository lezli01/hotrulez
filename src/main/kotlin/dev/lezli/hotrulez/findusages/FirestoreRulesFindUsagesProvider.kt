package dev.lezli.hotrulez.findusages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.lexer.FirestoreRulesParsingLexer
import dev.lezli.hotrulez.parser.FirestoreRulesTokenSets
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesLetStatement
import dev.lezli.hotrulez.psi.FirestoreRulesParameter
import dev.lezli.hotrulez.psi.FirestoreRulesPathWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesTypes
import dev.lezli.hotrulez.references.FirestoreRulesNamedElement

/**
 * Enables Find Usages for the four user-defined symbol kinds. The word scanner
 * over the parsing lexer indexes identifier occurrences; the standard usage
 * search then keeps only those whose [FirestoreRulesReference] resolves back to
 * the declaration, so path-variable shadowing is respected automatically.
 */
class FirestoreRulesFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            FirestoreRulesParsingLexer(),
            TokenSet.create(FirestoreRulesTypes.IDENTIFIER),
            FirestoreRulesTokenSets.COMMENTS,
            FirestoreRulesTokenSets.STRINGS,
        )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is FirestoreRulesNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is FirestoreRulesFunctionDeclaration -> "function"
        is FirestoreRulesParameter -> "parameter"
        is FirestoreRulesLetStatement -> "let binding"
        is FirestoreRulesPathWildcard, is FirestoreRulesRecursiveWildcard -> "path variable"
        else -> "symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
