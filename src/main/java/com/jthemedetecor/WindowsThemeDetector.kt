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
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinReg.HKEYByReference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Objects
import java.util.function.Consumer
import kotlin.concurrent.Volatile

/**
 * Determines the dark/light theme by the windows registry values through JNA.
 * Works on a Windows 10 system.
 *
 * @author Daniel Gyorffy
 * @author airsquared
 */
internal class WindowsThemeDetector : OsThemeDetector() {
    private val listeners: MutableSet<Consumer<Boolean?>?> = ConcurrentHashSet()

    @Volatile
    private var detectorThread: DetectorThread? = null

    override val isDark: Boolean
        get() = Advapi32Util.registryValueExists(
            WinReg.HKEY_CURRENT_USER,
            REGISTRY_PATH,
            REGISTRY_VALUE
        ) &&
                Advapi32Util.registryGetIntValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_PATH,
                    REGISTRY_VALUE
                ) == 0

    @Synchronized
    override fun registerListener(darkThemeListener: Consumer<Boolean?>) {
        Objects.requireNonNull(darkThemeListener)
        val listenerAdded = listeners.add(darkThemeListener)
        val singleListener = listenerAdded && listeners.size == 1
        val currentDetectorThread = detectorThread
        val threadInterrupted = currentDetectorThread != null && currentDetectorThread.isInterrupted

        if (singleListener || threadInterrupted) {
            val newDetectorThread = DetectorThread(this)
            this.detectorThread = newDetectorThread
            newDetectorThread.start()
        }
    }

    @Synchronized
    override fun removeListener(darkThemeListener: Consumer<Boolean?>?) {
        listeners.remove(darkThemeListener)
        if (listeners.isEmpty()) {
            detectorThread!!.interrupt()
            this.detectorThread = null
        }
    }

    /**
     * Thread implementation for detecting the theme changes
     */
    private class DetectorThread(private val themeDetector: WindowsThemeDetector) : Thread() {
        private var lastValue: Boolean

        init {
            this.lastValue = themeDetector.isDark
            this.name = "Windows 10 Theme Detector Thread"
            this.isDaemon = true
            this.priority = NORM_PRIORITY - 1
        }

        override fun run() {
            val hkey = HKEYByReference()
            var err = Advapi32.INSTANCE.RegOpenKeyEx(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_PATH,
                0,
                WinNT.KEY_READ,
                hkey
            )
            if (err != W32Errors.ERROR_SUCCESS) {
                throw Win32Exception(err)
            }

            while (!this.isInterrupted) {
                err = Advapi32.INSTANCE.RegNotifyChangeKeyValue(
                    hkey.value,
                    false,
                    WinNT.REG_NOTIFY_CHANGE_LAST_SET,
                    null,
                    false
                )
                if (err != W32Errors.ERROR_SUCCESS) {
                    throw Win32Exception(err)
                }

                val currentDetection = themeDetector.isDark
                if (currentDetection != this.lastValue) {
                    lastValue = currentDetection
                    logger.debug("Theme change detected: dark: {}", currentDetection)
                    for (listener in themeDetector.listeners) {
                        try {
                            listener!!.accept(currentDetection)
                        } catch (e: RuntimeException) {
                            logger.error("Caught exception during listener notifying ", e)
                        }
                    }
                }
            }
            Advapi32Util.registryCloseKey(hkey.value)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(
            WindowsThemeDetector::class.java
        )

        private const val REGISTRY_PATH =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
        private const val REGISTRY_VALUE = "AppsUseLightTheme"
    }
}
