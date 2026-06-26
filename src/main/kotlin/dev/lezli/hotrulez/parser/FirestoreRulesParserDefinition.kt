package dev.lezli.hotrulez.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.FirestoreRulesLanguage
import dev.lezli.hotrulez.lexer.FirestoreRulesParsingLexer
import dev.lezli.hotrulez.psi.FirestoreRulesFile
import dev.lezli.hotrulez.psi.FirestoreRulesTypes

class FirestoreRulesParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = FirestoreRulesParsingLexer()

    override fun createParser(project: Project): PsiParser = FirestoreRulesParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = FirestoreRulesTokenSets.WHITE_SPACES

    override fun getCommentTokens(): TokenSet = FirestoreRulesTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = FirestoreRulesTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement = FirestoreRulesTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = FirestoreRulesFile(viewProvider)

    companion object {
        val FILE: IFileElementType = IFileElementType(FirestoreRulesLanguage)
    }
}
