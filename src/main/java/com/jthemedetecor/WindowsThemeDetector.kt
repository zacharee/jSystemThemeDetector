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

import com.sun.jna.platform.win32.*
import com.sun.jna.platform.win32.WinReg.HKEYByReference

/**
 * Determines the dark/light theme by the windows registry values through JNA.
 * Works on a Windows 10 system.
 *
 * @author Daniel Gyorffy
 * @author airsquared
 */
class WindowsThemeDetector : ThreadBasedOsThemeDetector() {
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

    override fun createThread(): ThreadBasedOsThemeDetector.DetectorThread {
        return DetectorThread()
    }

    /**
     * Thread implementation for detecting the theme changes
     */
    private inner class DetectorThread : ThreadBasedOsThemeDetector.DetectorThread(
        threadName = "Windows Theme Detector",
        daemon = true,
        priority = NORM_PRIORITY - 1,
    ) {
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

                val currentDetection = isDark
                if (currentDetection != this.lastValue) {
                    lastValue = currentDetection
                    logger.debug("Theme change detected: dark: {}", currentDetection)
                    notifyListeners(currentDetection)
                }
            }
            Advapi32Util.registryCloseKey(hkey.value)
        }
    }

    companion object {
        private const val REGISTRY_PATH =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
        private const val REGISTRY_VALUE = "AppsUseLightTheme"
    }
}
