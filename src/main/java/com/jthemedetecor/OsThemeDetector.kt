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

import com.jthemedetecor.util.OsInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import oshi.annotation.concurrent.ThreadSafe
import java.util.function.Consumer
import kotlin.concurrent.Volatile

/**
 * For detecting the theme (dark/light) used by the Operating System.
 *
 * @author Daniel Gyorffy
 */
abstract class OsThemeDetector {
    @get:ThreadSafe
    abstract val isDark: Boolean

    /**
     * Registers a [Consumer] that will listen to a theme-change.
     *
     * @param darkThemeListener the [Consumer] that accepts a [Boolean] that represents
     * that the os using a dark theme or not
     */
    @ThreadSafe
    abstract fun registerListener(darkThemeListener: Consumer<Boolean>)

    /**
     * Removes the listener.
     */
    @ThreadSafe
    abstract fun removeListener(darkThemeListener: Consumer<Boolean>)

    private class EmptyDetector : OsThemeDetector() {
        override val isDark: Boolean
            get() = false

        override fun registerListener(darkThemeListener: Consumer<Boolean>) {
        }

        override fun removeListener(darkThemeListener: Consumer<Boolean>) {
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OsThemeDetector::class.java)

        @Volatile
        private var osThemeDetector: OsThemeDetector? = null

        @JvmStatic
        @get:ThreadSafe
        val detector: OsThemeDetector
            get() {
                var instance = osThemeDetector

                if (instance == null) {
                    synchronized(OsThemeDetector::class.java) {
                        instance = osThemeDetector
                        if (instance == null) {
                            instance = createDetector()
                            osThemeDetector = instance
                        }
                    }
                }

                return instance!!
            }

        private fun createDetector(): OsThemeDetector {
            return when {
                OsInfo.isWindows10OrLater -> {
                    logDetection("Windows 10", WindowsThemeDetector::class.java)
                    return WindowsThemeDetector()
                }

                OsInfo.isGnome -> {
                    logDetection("Gnome", GnomeThemeDetector::class.java)
                    return GnomeThemeDetector()
                }

                OsInfo.isKde -> {
                    logDetection("KDE", KdeThemeDetector::class.java)
                    return KdeThemeDetector()
                }

                OsInfo.isLXDE -> {
                    logDetection("LXDE", LXDEThemeDetector::class.java)
                    return LXDEThemeDetector()
                }

                OsInfo.isLinux -> {
                    logDetection("GenericLinux", GenericLinuxThemeDetector::class.java)
                    return GenericLinuxThemeDetector()
                }

                OsInfo.isMacOsMojaveOrLater -> {
                    logDetection("MacOS", MacOSThemeDetector::class.java)
                    return MacOSThemeDetector()
                }

                else -> {
                    logger.debug(
                        "Theme detection is not supported on the system: {} {}",
                        OsInfo.family,
                        OsInfo.version,
                    )
                    logger.debug("Creating empty detector...")
                    return EmptyDetector()
                }
            }
        }

        private fun logDetection(desktop: String, detectorClass: Class<out OsThemeDetector>) {
            logger.debug("Supported Desktop detected: {}", desktop)
            logger.debug("Creating {}...", detectorClass.name)
        }

        @get:ThreadSafe
        val isSupported: Boolean
            get() = OsInfo.isWindows10OrLater || OsInfo.isMacOsMojaveOrLater || OsInfo.isGnome || OsInfo.isLXDE || OsInfo.isLinux
    }
}
