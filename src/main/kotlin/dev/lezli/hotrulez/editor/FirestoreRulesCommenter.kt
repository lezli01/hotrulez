package dev.lezli.hotrulez.editor

import com.intellij.lang.Commenter

/**
 * Drives Comment with Line Comment (`//`) and Comment with Block Comment
 * (`/* */`) for Firestore Rules, matching the comment syntax accepted by the
 * lexer.
 */
class FirestoreRulesCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
