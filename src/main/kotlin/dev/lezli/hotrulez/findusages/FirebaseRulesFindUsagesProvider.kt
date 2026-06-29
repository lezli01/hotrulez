package dev.lezli.hotrulez.findusages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.lexer.FirebaseRulesParsingLexer
import dev.lezli.hotrulez.parser.FirebaseRulesTokenSets
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesLetStatement
import dev.lezli.hotrulez.psi.FirebaseRulesParameter
import dev.lezli.hotrulez.psi.FirebaseRulesPathWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesTypes
import dev.lezli.hotrulez.references.FirebaseRulesNamedElement

/**
 * Enables Find Usages for the four user-defined symbol kinds. The word scanner
 * over the parsing lexer indexes identifier occurrences; the standard usage
 * search then keeps only those whose [FirebaseRulesReference] resolves back to
 * the declaration, so path-variable shadowing is respected automatically.
 */
class FirebaseRulesFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            FirebaseRulesParsingLexer(),
            TokenSet.create(FirebaseRulesTypes.IDENTIFIER),
            FirebaseRulesTokenSets.COMMENTS,
            FirebaseRulesTokenSets.STRINGS,
        )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is FirebaseRulesNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is FirebaseRulesFunctionDeclaration -> "function"
        is FirebaseRulesParameter -> "parameter"
        is FirebaseRulesLetStatement -> "let binding"
        is FirebaseRulesPathWildcard, is FirebaseRulesRecursiveWildcard -> "path variable"
        else -> "symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
