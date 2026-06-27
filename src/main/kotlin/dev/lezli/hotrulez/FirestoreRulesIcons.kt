package dev.lezli.hotrulez

import com.intellij.openapi.util.IconLoader

/**
 * Icon holder for the Firestore Rules file type. The SVG lives under
 * `src/main/resources/icons` and is loaded once, lazily, through [IconLoader].
 */
object FirestoreRulesIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/firestoreRules.svg", FirestoreRulesIcons::class.java)
}
