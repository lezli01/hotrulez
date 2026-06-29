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
import dev.lezli.hotrulez.FirebaseRulesLanguage
import dev.lezli.hotrulez.lexer.FirebaseRulesParsingLexer
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesTypes

class FirebaseRulesParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = FirebaseRulesParsingLexer()

    override fun createParser(project: Project): PsiParser = FirebaseRulesParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = FirebaseRulesTokenSets.WHITE_SPACES

    override fun getCommentTokens(): TokenSet = FirebaseRulesTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = FirebaseRulesTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement = FirebaseRulesTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = FirebaseRulesFile(viewProvider)

    companion object {
        val FILE: IFileElementType = IFileElementType(FirebaseRulesLanguage)
    }
}
