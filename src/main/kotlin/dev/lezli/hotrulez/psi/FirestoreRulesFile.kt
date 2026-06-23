package dev.lezli.hotrulez.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import dev.lezli.hotrulez.FirestoreRulesFileType
import dev.lezli.hotrulez.FirestoreRulesLanguage

class FirestoreRulesFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, FirestoreRulesLanguage) {
    override fun getFileType(): FirestoreRulesFileType = FirestoreRulesFileType

    override fun toString(): String = "Firestore Rules File"
}
