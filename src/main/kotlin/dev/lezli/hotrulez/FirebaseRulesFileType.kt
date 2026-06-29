package dev.lezli.hotrulez

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object FirebaseRulesFileType : LanguageFileType(FirebaseRulesLanguage) {
    override fun getName(): String = "Firestore Rules"

    override fun getDescription(): String = "Firebase Cloud Firestore Security Rules"

    override fun getDefaultExtension(): String = "rules"

    override fun getIcon(): Icon = FirebaseRulesIcons.FILE
}
