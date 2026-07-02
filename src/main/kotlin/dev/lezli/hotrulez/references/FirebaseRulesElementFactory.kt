package dev.lezli.hotrulez.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import dev.lezli.hotrulez.FirebaseRulesFileType
import dev.lezli.hotrulez.psi.FirebaseRulesAllowStatement
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMatchPath
import dev.lezli.hotrulez.psi.FirebaseRulesReturnStatement
import dev.lezli.hotrulez.psi.FirebaseRulesRulesVersionStatement
import dev.lezli.hotrulez.psi.FirebaseRulesServiceDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesServiceName

/**
 * Synthesises throwaway PSI fragments used to produce replacement/insertion nodes for
 * rename and quick-fixes. Each fragment is parsed from a minimal, well-formed `.rules`
 * snippet and the desired node extracted from it; the fragment is non-physical, so it is
 * safe to graft into a (writable) file inside a rename or ModCommand action.
 */
object FirebaseRulesElementFactory {
    /**
     * A standalone `IDENTIFIER` leaf carrying [name], extracted from a throwaway
     * `function <name>() { ... }` file. In the IDE the names validator rejects an
     * illegal [name] before a rename reaches here; if a caller bypasses that (e.g. a
     * programmatic rename to a keyword), the synthesised function has no name
     * identifier, and a platform-friendly [IncorrectOperationException] is thrown
     * rather than a bare error.
     */
    fun identifier(project: Project, name: String): PsiElement {
        val file = file(project, "function $name() { return true; }")
        val identifier = PsiTreeUtil.findChildOfType(file, FirebaseRulesFunctionDeclaration::class.java)?.identifier
        return identifier ?: throw IncorrectOperationException("'$name' is not a valid Firebase Rules identifier")
    }

    /** A throwaway parsed file over [text], used only to extract synthesised fragments. */
    fun file(project: Project, text: String): FirebaseRulesFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText("_hotrulez_dummy.rules", FirebaseRulesFileType, text) as FirebaseRulesFile

    /** A `rules_version = '<version>';` statement. */
    fun rulesVersionStatement(project: Project, version: String = "2"): FirebaseRulesRulesVersionStatement =
        extract(project, "rules_version = '$version';")

    /** A `service_name` node (e.g. `cloud.firestore`), for replacing an unknown service name. */
    fun serviceName(project: Project, name: String): FirebaseRulesServiceName =
        extract(project, "service $name { }")

    /** A `service` block containing [rootMatchPath] as its sole root match, for a block-less service. */
    fun serviceBlock(project: Project, rootMatchPath: String): PsiElement =
        extract<FirebaseRulesServiceDeclaration>(project, "service cloud.firestore {\n  match $rootMatchPath {\n  }\n}").block
            ?: throw IncorrectOperationException("synthesised service has no block")

    /** A root/nested `match <path> { }` declaration. */
    fun matchDeclaration(project: Project, path: String): FirebaseRulesMatchDeclaration =
        extract(project, "match $path {\n}")

    /** A `match_path` node (e.g. `/songs/{id}`), for reordering path segments. */
    fun matchPath(project: Project, path: String): FirebaseRulesMatchPath =
        extract(project, "match $path {\n}")

    /** A `return <expression>;` statement (default `return false;`). */
    fun returnStatement(project: Project, expressionText: String = "false"): FirebaseRulesReturnStatement =
        extract(project, "function _f() { return $expressionText; }")

    /** An `allow <methods>: if <condition>;` statement, for adding a missing condition. */
    fun allowStatement(
        project: Project,
        methodsText: String,
        conditionText: String = "false",
    ): FirebaseRulesAllowStatement =
        extract(project, "match /_ {\n  allow $methodsText: if $conditionText;\n}")

    /** A `function <name>(<params>) { return false; }` declaration, for the create-function fix. */
    fun functionDeclaration(
        project: Project,
        name: String,
        parameters: List<String>,
    ): FirebaseRulesFunctionDeclaration =
        extract(project, "function $name(${parameters.joinToString(", ")}) {\n  return false;\n}")

    private inline fun <reified T : PsiElement> extract(project: Project, text: String): T =
        PsiTreeUtil.findChildOfType(file(project, text), T::class.java)
            ?: throw IncorrectOperationException("could not synthesise ${T::class.simpleName} from: $text")
}
