package dev.lezli.hotrulez.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import dev.lezli.hotrulez.FirebaseRulesFileType
import dev.lezli.hotrulez.FirebaseRulesLanguage

class FirebaseRulesFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, FirebaseRulesLanguage) {
    override fun getFileType(): FirebaseRulesFileType = FirebaseRulesFileType

    override fun toString(): String = "Firestore Rules File"
}
