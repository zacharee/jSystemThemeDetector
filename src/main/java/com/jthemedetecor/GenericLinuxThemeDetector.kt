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

import com.jthemedetecor.util.ConcurrentHashSet
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Objects
import java.util.function.Consumer
import java.util.regex.Pattern

class GenericLinuxThemeDetector : OsThemeDetector() {
    private val listeners = ConcurrentHashSet<Consumer<Boolean>>()
    private val darkThemeNamePattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE)
    private var detectorThread: DetectorThread? = null

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

    @Synchronized
    override fun registerListener(darkThemeListener: Consumer<Boolean>) {
        Objects.requireNonNull(darkThemeListener)
        val listenerAdded = listeners.add(darkThemeListener)
        val singleListener = listenerAdded && listeners.size == 1
        val threadInterrupted = detectorThread != null && detectorThread!!.isInterrupted
        if (singleListener || threadInterrupted) {
            detectorThread = DetectorThread(this)
            detectorThread!!.start()
        }
    }

    @Synchronized
    override fun removeListener(darkThemeListener: Consumer<Boolean>) {
        listeners.remove(darkThemeListener)
        if (listeners.isEmpty()) {
            detectorThread!!.interrupt()
            detectorThread = null
        }
    }

    /**
     * Thread implementation for detecting the actually changed theme
     */
    private class DetectorThread(private val detector: GenericLinuxThemeDetector) :
        Thread() {
        private var lastValue: Boolean

        init {
            lastValue = detector.isDark
            name = "GTK Theme Detector Thread"
            this.isDaemon = true
            priority = NORM_PRIORITY - 1
        }

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
                        val currentDetection = detector.isDarkTheme(value ?: "")
                        println("Theme changed detection, dark: $currentDetection")
                        if (currentDetection != lastValue) {
                            lastValue = currentDetection
                            for (listener in detector.listeners) {
                                try {
                                    listener.accept(currentDetection)
                                } catch (e: RuntimeException) {
                                    println("Caught exception during listener notifying ")
                                    e.printStackTrace()
                                }
                            }
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