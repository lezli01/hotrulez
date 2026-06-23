package dev.lezli.hotrulez

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object FirestoreRulesFileType : LanguageFileType(FirestoreRulesLanguage) {
    override fun getName(): String = "Firestore Rules"

    override fun getDescription(): String = "Firebase Cloud Firestore Security Rules"

    override fun getDefaultExtension(): String = "rules"

    override fun getIcon(): Icon? = null
}
