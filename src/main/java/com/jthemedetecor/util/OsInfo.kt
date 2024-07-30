package com.jthemedetecor.util

import com.jthemedetecor.OsThemeDetector
import io.github.g00fy2.versioncompare.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import oshi.PlatformEnum
import oshi.SystemInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

object OsInfo {
    private val logger: Logger = LoggerFactory.getLogger(OsThemeDetector::class.java)

    private val platformType: PlatformEnum
    val family: String
    val version: String

    init {
        val systemInfo = SystemInfo()
        val osInfo = systemInfo.operatingSystem
        val osVersionInfo = osInfo.versionInfo

        platformType = SystemInfo.getCurrentPlatform()
        family = osInfo.family
        version = osVersionInfo.version
    }

    val isWindows10OrLater: Boolean
        get() = hasTypeAndVersionOrHigher(PlatformEnum.WINDOWS, "10")

    val isLinux: Boolean
        get() = hasType(PlatformEnum.LINUX)

    val isMacOsMojaveOrLater: Boolean
        get() = hasTypeAndVersionOrHigher(PlatformEnum.MACOS, "10.14")

    val isGnome: Boolean
        get() = isLinux && (queryResultContains("echo \$XDG_CURRENT_DESKTOP", "gnome") ||
                queryResultContains("echo \$XDG_DATA_DIRS | grep -Eo 'gnome'", "gnome"))

    val isLXDE: Boolean
        get() = isLinux && (queryResultContains("echo \$XDG_CURRENT_DESKTOP", "LXDE") ||
                queryResultContains("echo \$XDG_DATA_DIRS | grep -Eo 'LXDE'", "LXDE"))

    fun hasType(platformType: PlatformEnum): Boolean {
        return OsInfo.platformType == platformType
    }

    fun isVersionAtLeast(version: String?): Boolean {
        return Version(OsInfo.version).isAtLeast(version)
    }

    fun hasTypeAndVersionOrHigher(platformType: PlatformEnum, version: String?): Boolean {
        return hasType(platformType) && isVersionAtLeast(version)
    }

    private fun queryResultContains(cmd: String, subResult: String): Boolean {
        return query(cmd).lowercase(Locale.getDefault()).contains(subResult)
    }

    private fun query(cmd: String): String {
        try {
            val process = Runtime.getRuntime().exec(cmd)
            val stringBuilder = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var actualReadLine: String?
                while ((reader.readLine().also { actualReadLine = it }) != null) {
                    if (stringBuilder.length != 0) stringBuilder.append('\n')
                    stringBuilder.append(actualReadLine)
                }
            }
            return stringBuilder.toString()
        } catch (e: IOException) {
            logger.error("Exception caught while querying the OS", e)
            return ""
        }
    }
}
