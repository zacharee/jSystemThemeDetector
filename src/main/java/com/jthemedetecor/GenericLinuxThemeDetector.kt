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

class GenericLinuxThemeDetector : ThreadBasedOsThemeDetector() {
    private val darkThemeNamePattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE)

    override val isDark: Boolean
        get() {
            try {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec(GET_CMD)
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val readLine = reader.readLine()
                    if (readLine != null) {
                        return isDarkTheme(readLine)
                    }
                }
            } catch (e: IOException) {
                println("Couldn't detect Linux OS theme")
                e.printStackTrace()
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
        threadName = "Generic Linux Theme Detector",
        daemon = true,
        priority = NORM_PRIORITY - 1,
    ) {
        @Suppress("NewApi")
        override fun run() {
            try {
                val runtime = Runtime.getRuntime()
                val monitoringProcess = runtime.exec(MONITORING_CMD)
                BufferedReader(InputStreamReader(monitoringProcess.inputStream)).use { reader ->
                    while (!this.isInterrupted) {
                        //Expected input = gtk-theme: '$GtkThemeName'
                        val readLine = reader.readLine() ?: ""
                        val keyValue =
                            readLine.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        val value = keyValue.getOrNull(1)
                        val currentDetection = isDarkTheme(value ?: "")
                        println("Theme changed detection, dark: $currentDetection")
                        if (currentDetection != lastValue) {
                            lastValue = currentDetection
                            notifyListeners(currentDetection)
                        }
                    }
                    println("ThemeDetectorThread has been interrupted!")
                    if (monitoringProcess.isAlive) {
                        monitoringProcess.destroy()
                        println("Monitoring process has been destroyed!")
                    }
                }
            } catch (e: IOException) {
                println("Couldn't start monitoring process ")
                e.printStackTrace()
            } catch (e: ArrayIndexOutOfBoundsException) {
                println("Couldn't parse command line output")
                e.printStackTrace()
            }
        }
    }

    companion object {
        private val MONITORING_CMD = arrayOf("gsettings", "monitor", "org.gnome.desktop.interface", "icon-theme")
        private val GET_CMD = arrayOf("gsettings", "get", "org.gnome.desktop.interface", "icon-theme")
    }
}