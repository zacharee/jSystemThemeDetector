/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jthemedetecor

import com.sun.jna.Callback
import de.jangassen.jfa.foundation.Foundation
import de.jangassen.jfa.foundation.Foundation.NSAutoreleasePool
import de.jangassen.jfa.foundation.ID
import java.util.regex.Pattern

/**
 * Determines the dark/light theme on a MacOS System through the *Apple Foundation framework*.
 *
 * @author Daniel Gyorffy
 */
class MacOSThemeDetector : OsThemeDetector() {
    private val themeNamePattern: Pattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE)

    private val themeChangedCallback: Callback = object : Callback {
        @Suppress("unused")
        fun callback() {
            notifyListeners(isDark)
        }
    }

    init {
        initObserver()
    }

    private fun initObserver() {
        val pool = NSAutoreleasePool()
        try {
            val delegateClass = Foundation.allocateObjcClassPair(
                Foundation.getObjcClass("NSObject"),
                "NSColorChangesObserver",
            )
            if (ID.NIL != delegateClass) {
                if (!Foundation.addMethod(
                        delegateClass,
                        Foundation.createSelector("handleAppleThemeChanged:"),
                        themeChangedCallback,
                        "v@"
                    )
                ) {
                    logger.error("Observer method cannot be added")
                }
                Foundation.registerObjcClassPair(delegateClass)
            }

            val delegate = Foundation.invoke("NSColorChangesObserver", "new")
            Foundation.invoke(
                Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter"),
                "addObserver:selector:name:object:",
                delegate,
                Foundation.createSelector("handleAppleThemeChanged:"),
                Foundation.nsString("AppleInterfaceThemeChangedNotification"),
                ID.NIL
            )
        } finally {
            pool.drain()
        }
    }

    override val isDark: Boolean
        get() {
            val pool = NSAutoreleasePool()
            try {
                val userDefaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
                val appleInterfaceStyle = Foundation.toStringViaUTF8(
                    Foundation.invoke(
                        userDefaults,
                        "objectForKey:",
                        Foundation.nsString("AppleInterfaceStyle")
                    )
                )
                return isDarkTheme(appleInterfaceStyle)
            } catch (e: RuntimeException) {
                logger.error("Couldn't execute theme name query with the Os", e)
            } finally {
                pool.drain()
            }
            return false
        }

    private fun isDarkTheme(themeName: String?): Boolean {
        return themeName != null && themeNamePattern.matcher(themeName).matches()
    }
}

