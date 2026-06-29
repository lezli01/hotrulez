package dev.lezli.hotrulez

import com.intellij.openapi.util.IconLoader

/**
 * Icon holder for the Firestore Rules file type. The SVG lives under
 * `src/main/resources/icons` and is loaded once, lazily, through [IconLoader].
 */
object FirebaseRulesIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/firebaseRules.svg", FirebaseRulesIcons::class.java)
}
