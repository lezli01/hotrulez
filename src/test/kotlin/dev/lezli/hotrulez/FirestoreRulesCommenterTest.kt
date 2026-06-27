package dev.lezli.hotrulez

import dev.lezli.hotrulez.editor.FirestoreRulesCommenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreRulesCommenterTest {
    private val commenter = FirestoreRulesCommenter()

    @Test
    fun usesSlashLineComments() {
        assertEquals("//", commenter.lineCommentPrefix)
    }

    @Test
    fun usesCStyleBlockComments() {
        assertEquals("/*", commenter.blockCommentPrefix)
        assertEquals("*/", commenter.blockCommentSuffix)
    }

    @Test
    fun doesNotEscapeNestedBlockComments() {
        assertNull(commenter.commentedBlockCommentPrefix)
        assertNull(commenter.commentedBlockCommentSuffix)
    }
}
