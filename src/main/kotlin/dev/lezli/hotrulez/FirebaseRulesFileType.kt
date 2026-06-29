package dev.lezli.hotrulez

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object FirebaseRulesFileType : LanguageFileType(FirebaseRulesLanguage) {
    override fun getName(): String = "Firebase Rules"

    override fun getDescription(): String = "Firebase Security Rules (Cloud Firestore and Cloud Storage)"

    override fun getDefaultExtension(): String = "rules"

    override fun getIcon(): Icon = FirebaseRulesIcons.FILE
}
