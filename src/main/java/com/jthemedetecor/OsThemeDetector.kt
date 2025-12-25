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

    protected val listeners = ConcurrentHashSet<Consumer<Boolean>>()
    protected val logger = LoggerFactory.getLogger(this::class.java)

    protected open val needsMonitorRestart: Boolean = false

    /**
     * Registers a [Consumer] that will listen to a theme-change.
     *
     * @param darkThemeListener the [Consumer] that accepts a [Boolean] that represents
     * that the os using a dark theme or not
     */
    @ThreadSafe
    @Synchronized
    fun registerListener(darkThemeListener: Consumer<Boolean>) {
        val listenerAdded = listeners.add(darkThemeListener)
        val singleListener = listenerAdded && listeners.size == 1

        if (singleListener || needsMonitorRestart) {
            startMonitor()
        }
    }

    /**
     * Removes the listener.
     */
    @ThreadSafe
    @Synchronized
    fun removeListener(darkThemeListener: Consumer<Boolean>) {
        listeners.remove(darkThemeListener)
        if (listeners.isEmpty()) {
            stopMonitor()
        }
    }

    protected fun notifyListeners(newValue: Boolean = isDark) {
        listeners.forEach { it.accept(newValue) }
    }

    protected open fun startMonitor() {}
    protected open fun stopMonitor() {}

    private class EmptyDetector : OsThemeDetector() {
        override val isDark: Boolean
            get() = false

        override fun startMonitor() {}
        override fun stopMonitor() {}
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OsThemeDetector::class.java)

        @Volatile
        private var osThemeDetector: OsThemeDetector? = null

        private fun createDetector(): OsThemeDetector {
            return when {
                OsInfo.isWindows10OrLater -> {
                    logDetection("Windows 10", WindowsThemeDetector::class.java)
                    WindowsThemeDetector()
                }

                OsInfo.isKde -> {
                    logDetection("KDE", KdeThemeDetector::class.java)
                    return KdeThemeDetector()
                }

                OsInfo.isLXDE -> {
                    logDetection("LXDE", LXDEThemeDetector::class.java)
                    LXDEThemeDetector()
                }

                OsInfo.isGnome -> {
                    logDetection("Gnome", GnomeThemeDetector::class.java)
                    GnomeThemeDetector()
                }

                OsInfo.isLinux -> {
                    logDetection("GenericLinux", GenericLinuxThemeDetector::class.java)
                    GenericLinuxThemeDetector()
                }

                OsInfo.isMacOsMojaveOrLater -> {
                    logDetection("MacOS", MacOSThemeDetector::class.java)
                    MacOSThemeDetector()
                }

                else -> {
                    logger.debug(
                        "Theme detection is not supported on the system: {} {}",
                        OsInfo.family,
                        OsInfo.version,
                    )
                    logger.debug("Creating empty detector...")
                    EmptyDetector()
                }
            }
        }

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

        private fun logDetection(desktop: String, detectorClass: Class<out OsThemeDetector>) {
            logger.debug("Supported Desktop detected: {}", desktop)
            logger.debug("Creating {}...", detectorClass.name)
        }

        @get:ThreadSafe
        val isSupported: Boolean
            get() = OsInfo.isWindows10OrLater ||
                    OsInfo.isMacOsMojaveOrLater ||
                    OsInfo.isGnome ||
                    OsInfo.isKde ||
                    OsInfo.isLXDE ||
                    OsInfo.isLinux
    }
}

abstract class ThreadBasedOsThemeDetector : OsThemeDetector() {
    @Volatile
    protected var detectorThread: DetectorThread? = null

    override val needsMonitorRestart: Boolean
        get() = detectorThread?.isInterrupted == true

    protected abstract fun createThread(): DetectorThread

    override fun startMonitor() {
        detectorThread = createThread()
        detectorThread?.start()
    }

    override fun stopMonitor() {
        detectorThread?.interrupt()
        detectorThread = null
    }

    protected abstract inner class DetectorThread(
        threadName: String,
        daemon: Boolean,
        priority: Int,
    ) : Thread() {
        protected var lastValue = this@ThreadBasedOsThemeDetector.isDark

        init {
            this.name = threadName
            this.isDaemon = daemon
            this.priority = priority
        }
    }
}
