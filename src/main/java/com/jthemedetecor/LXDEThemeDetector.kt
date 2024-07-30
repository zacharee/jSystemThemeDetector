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
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.function.Consumer

class LXDEThemeDetector : OsThemeDetector() {
    private val listeners = ConcurrentHashSet<Consumer<Boolean>>()
    private var directoryWatcher: DirectoryWatcher? = null

    override val isDark: Boolean
        get() {
            try {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec(GET_CMD)
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val contents = reader.readText()
                    return isDarkTheme(contents)
                }
            } catch (e: IOException) {
                println("Couldn't detect Linux OS theme")
                e.printStackTrace()
            }
            return false
        }

    private fun isDarkTheme(fileContents: String): Boolean {
        return fileContents.lines().any { it.startsWith("sNet/ThemeName") && it.contains("dark", true) }
    }

    @Synchronized
    override fun registerListener(darkThemeListener: Consumer<Boolean>) {
        val listenerAdded = listeners.add(darkThemeListener)
        val singleListener = listenerAdded && listeners.size == 1
        if (singleListener) {
            directoryWatcher = DirectoryWatcher.builder()
                .path(File("~/.config/lxsession/LXDE/").toPath())
                .listener { event ->
                    if (event.eventType() == DirectoryChangeEvent.EventType.MODIFY && event.path()
                            .endsWith("desktop.conf")
                    ) {
                        val isDark = isDark
                        listeners.forEach { listener -> listener.accept(isDark) }
                    }
                }
                .build()

            directoryWatcher?.watchAsync()
        }
    }

    @Synchronized
    override fun removeListener(darkThemeListener: Consumer<Boolean>) {
        listeners.remove(darkThemeListener)
        if (listeners.isEmpty()) {
            directoryWatcher?.close()
            directoryWatcher = null
        }
    }

    companion object {
        private val GET_CMD = arrayOf("cat", "~/.config/lxsession/LXDE/desktop.conf")
    }
}