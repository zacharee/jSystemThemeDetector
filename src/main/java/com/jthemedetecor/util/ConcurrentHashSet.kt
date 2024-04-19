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
package com.jthemedetecor.util

import java.util.concurrent.ConcurrentHashMap

class ConcurrentHashSet<E> : MutableSet<E> {
    private val map: MutableMap<E, Any?> = ConcurrentHashMap()

    override val size: Int
        get() = map.size

    override fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    override fun contains(element: E): Boolean {
        return map.containsKey(element)
    }

    override fun iterator(): MutableIterator<E> {
        return map.keys.iterator()
    }

    override fun add(element: E): Boolean {
        return map.put(element, OBJ) == null
    }

    override fun remove(element: E): Boolean {
        return map.remove(element) != null
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return map.keys.containsAll(elements)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        var changed = false
        for (e in elements) {
            if (map.put(e, OBJ) == null) {
                changed = true
            }
        }
        return changed
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        map.clear()
    }

    companion object {
        private val OBJ = Any()
    }
}