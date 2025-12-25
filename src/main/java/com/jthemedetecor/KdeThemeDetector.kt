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

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Used for detecting the dark theme on a Linux KDE desktop environment.
 * Tested on Ubuntu KDE Plasma (kde-plasma-desktop).
 *
 * @author Thomas Sartre
 * @see GnomeThemeDetector
 */
class KdeThemeDetector : ThreadBasedOsThemeDetector() {
    private val darkThemeNamePattern: Pattern =
        Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE)

    override val isDark: Boolean
        get() {
            try {
                val process: Process =
                    Runtime.getRuntime().exec(GET_THEME_CMD)
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val theme = reader.readLine()
                    if (theme != null && isDarkTheme(theme)) {
                        return true
                    }
                }
            } catch (e: IOException) {
                logger.error("Couldn't detect KDE OS theme", e)
            }
            return false
        }

    override fun createThread(): ThreadBasedOsThemeDetector.DetectorThread {
        return DetectorThread(this)
    }

    private fun isDarkTheme(theme: String): Boolean {
        return darkThemeNamePattern.matcher(theme).matches()
    }

    /**
     * Thread implementation for detecting the actually changed theme.
     */
    private inner class DetectorThread(private val detector: KdeThemeDetector) :
        ThreadBasedOsThemeDetector.DetectorThread(
            threadName = "KDE Theme Detector Thread",
            daemon = true,
            priority = NORM_PRIORITY - 1,
        ) {
        override fun run() {
            while (!this.isInterrupted) {
                val currentDetection = detector.isDark
                if (currentDetection != lastValue) {
                    lastValue = currentDetection
                    for (listener in detector.listeners) {
                        try {
                            listener.accept(currentDetection)
                        } catch (e: RuntimeException) {
                            logger.error("Caught exception during listener notification", e)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val GET_THEME_CMD =
            "kreadconfig5 --file kdeglobals --group General --key ColorScheme"
    }
}
