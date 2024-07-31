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
 * Used for detecting the dark theme on a Linux (GNOME/GTK) system.
 * Tested on Ubuntu.
 *
 * @author Daniel Gyorffy
 */
class GnomeThemeDetector : ThreadBasedOsThemeDetector() {
    private val darkThemeNamePattern: Pattern =
        Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE)

    override val isDark: Boolean
        get() {
            try {
                val runtime = Runtime.getRuntime()
                for (cmd in GET_CMD) {
                    val process = runtime.exec(cmd)
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        val readLine = reader.readLine()
                        if (readLine != null && isDarkTheme(readLine)) {
                            return true
                        }
                    }
                }
            } catch (e: IOException) {
                logger.error("Couldn't detect Linux OS theme", e)
            }
            return false
        }

    private fun isDarkTheme(gtkTheme: String): Boolean {
        return darkThemeNamePattern.matcher(gtkTheme).matches()
    }

    override fun createThread(): ThreadBasedOsThemeDetector.DetectorThread {
        return DetectorThread()
    }

    /**
     * Thread implementation for detecting the actually changed theme
     */
    private inner class DetectorThread : ThreadBasedOsThemeDetector.DetectorThread(
        threadName = "GTK Theme Detector",
        daemon = true,
        priority = NORM_PRIORITY - 1,
    ) {
        private val outputPattern: Pattern =
            Pattern.compile("(gtk-theme|color-scheme).*", Pattern.CASE_INSENSITIVE)

        override fun run() {
            try {
                val runtime = Runtime.getRuntime()
                val monitoringProcess = runtime.exec(MONITORING_CMD)
                BufferedReader(InputStreamReader(monitoringProcess.inputStream)).use { reader ->
                    while (!this.isInterrupted) {
                        //Expected input = gtk-theme: '$GtkThemeName'
                        val readLine = reader.readLine() ?: continue


                        // reader.readLine sometimes returns null on application shutdown.

                        if (!outputPattern.matcher(readLine).matches()) {
                            continue
                        }
                        val keyValue =
                            readLine.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        val value = keyValue[1]
                        val currentDetection = isDarkTheme(value)
                        logger.debug("Theme changed detection, dark: {}", currentDetection)
                        if (currentDetection != lastValue) {
                            lastValue = currentDetection
                            notifyListeners(currentDetection)
                        }
                    }
                    logger.debug("ThemeDetectorThread has been interrupted!")
                    if (monitoringProcess.isAlive) {
                        monitoringProcess.destroy()
                        logger.debug("Monitoring process has been destroyed!")
                    }
                }
            } catch (e: IOException) {
                logger.error("Couldn't start monitoring process ", e)
            } catch (e: ArrayIndexOutOfBoundsException) {
                logger.error("Couldn't parse command line output", e)
            }
        }
    }

    companion object {
        private const val MONITORING_CMD = "gsettings monitor org.gnome.desktop.interface"
        private val GET_CMD = arrayOf(
            "gsettings get org.gnome.desktop.interface gtk-theme",
            "gsettings get org.gnome.desktop.interface color-scheme"
        )
    }
}
