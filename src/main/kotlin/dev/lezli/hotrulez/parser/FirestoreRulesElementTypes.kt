package dev.lezli.hotrulez.parser

import com.intellij.psi.tree.IFileElementType
import dev.lezli.hotrulez.FirestoreRulesLanguage

object FirestoreRulesElementTypes {
    val FILE = IFileElementType(FirestoreRulesLanguage)
    val RULES_VERSION_DECLARATION = FirestoreRulesElementType("RULES_VERSION_DECLARATION")
    val SERVICE_DECLARATION = FirestoreRulesElementType("SERVICE_DECLARATION")
    val BLOCK = FirestoreRulesElementType("BLOCK")
    val MATCH_BLOCK = FirestoreRulesElementType("MATCH_BLOCK")
    val MATCH_PATH = FirestoreRulesElementType("MATCH_PATH")
    val PATH_WILDCARD = FirestoreRulesElementType("PATH_WILDCARD")
    val RECURSIVE_WILDCARD = FirestoreRulesElementType("RECURSIVE_WILDCARD")
    val ALLOW_STATEMENT = FirestoreRulesElementType("ALLOW_STATEMENT")
    val OPERATION_LIST = FirestoreRulesElementType("OPERATION_LIST")
    val FUNCTION_DECLARATION = FirestoreRulesElementType("FUNCTION_DECLARATION")
    val PARAMETER_LIST = FirestoreRulesElementType("PARAMETER_LIST")
    val RETURN_STATEMENT = FirestoreRulesElementType("RETURN_STATEMENT")
    val EXPRESSION = FirestoreRulesElementType("EXPRESSION")
    val UNKNOWN_STATEMENT = FirestoreRulesElementType("UNKNOWN_STATEMENT")
}
