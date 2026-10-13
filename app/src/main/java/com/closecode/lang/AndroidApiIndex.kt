package com.closecode.lang

enum class MemberKind { METHOD, FIELD }

data class ApiMember(
    val name: String,
    val kind: MemberKind,
    val arity: Int,
    val type: String,
    val params: List<String> = emptyList(),
) {
    fun display(): String = when (kind) {
        MemberKind.FIELD -> "$name: $type"
        MemberKind.METHOD -> {
            val p = if (params.isEmpty()) "" else params.joinToString(", ", "(", ")")
            "$name$p: $type"
        }
    }
}

data class ApiClass(
    val fqn: String,
    val simpleName: String,
    val superFqn: String?,
    val members: List<ApiMember>,
)

class AndroidApiIndex(
    private val classesByFqn: Map<String, ApiClass>,
    private val fqnsBySimpleName: Map<String, List<String>>,
    private val membersByName: Map<String, List<ApiMember>> = emptyMap(),
) {
    val classCount: Int get() = classesByFqn.size

    val memberCount: Int get() = classesByFqn.values.sumOf { it.members.size }

    val knownSimpleNames: Set<String> get() = fqnsBySimpleName.keys

    fun classesNamed(simpleName: String): List<ApiClass> =
        fqnsBySimpleName[simpleName].orEmpty().mapNotNull { classesByFqn[it] }

    fun hasClass(simpleName: String): Boolean = fqnsBySimpleName.containsKey(simpleName)

    /**
     * Fully-qualified names a bare [simpleName] could refer to, best candidate first. A name like
     * `Cursor` exists in several packages, so plain Android packages are preferred over obscure
     * ones to keep the common import at the top of the quick-fix list.
     */
    fun candidateFqns(simpleName: String): List<String> {
        val candidates = fqnsBySimpleName[simpleName].orEmpty()
        if (candidates.size <= 1) return candidates
        return candidates.sortedWith(
            compareBy(
                { if (it.startsWith("android.")) 0 else if (it.startsWith("androidx.")) 1 else 2 },
                { it.count { ch -> ch == '.' } },
                { it },
            ),
        )
    }

    /** The package a simple name lives in, for display in the completion popup. */
    fun packageOf(simpleName: String): String? {
        val best = candidateFqns(simpleName).firstOrNull() ?: return null
        return best.substringBeforeLast('.', "").ifEmpty { null }
    }

    fun membersOfFq(fqn: String, limit: Int = 3000): List<ApiMember> {
        val out = LinkedHashSet<ApiMember>()
        val seen = HashSet<String>()
        var cursor: String? = fqn
        var hops = 0
        while (cursor != null && hops++ < 12 && seen.add(cursor)) {
            val cls = classesByFqn[cursor] ?: break
            out.addAll(cls.members)
            cursor = cls.superFqn
        }
        return out.take(limit)
    }

    fun membersOf(simpleName: String, limit: Int = 3000): List<ApiMember> {
        val out = LinkedHashSet<ApiMember>()
        for (cls in classesNamed(simpleName)) {
            out.addAll(membersOfFq(cls.fqn, limit))
            if (out.size >= limit) break
        }
        return out.take(limit)
    }

    /**
     * Members declared under [name], whichever class owns them.
     *
     * This is the lookup a signature tooltip needs: the reader has written a call, and the buffer
     * says only the method's name. Android members are overloaded often enough that the call is a
     * list, not a single answer, so the caller picks.
     */
    fun membersNamed(name: String, limit: Int = 12): List<ApiMember> =
        membersByName[name].orEmpty().take(limit)

    companion object {
        val EMPTY = AndroidApiIndex(emptyMap(), emptyMap(), emptyMap())

        fun parse(text: String): AndroidApiIndex {
            val classes = LinkedHashMap<String, ApiClass>()
            val simple = LinkedHashMap<String, MutableList<String>>()
            val members = LinkedHashMap<String, MutableList<ApiMember>>()

            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '#') return@forEach
                val parts = line.split('|')
                when (parts[0]) {
                    "C" -> {
                        if (parts.size < 4) return@forEach
                        val fqn = parts[1]
                        classes[fqn] = ApiClass(fqn, parts[2], parts[3].ifEmpty { null }, emptyList())
                        simple.getOrPut(parts[2]) { ArrayList() }.let {
                            if (!it.contains(fqn)) it.add(fqn)
                        }
                    }

                    "M" -> {
                        if (parts.size < 5) return@forEach
                        val params = if (parts.size > 5 && parts[5].isNotEmpty()) {
                            parts[5].split(';')
                        } else emptyList()
                        members.getOrPut(parts[1]) { ArrayList() }.add(
                            ApiMember(parts[2], MemberKind.METHOD, parts[3].toIntOrNull() ?: 0, parts[4], params),
                        )
                    }

                    "F" -> {
                        if (parts.size < 4) return@forEach
                        members.getOrPut(parts[1]) { ArrayList() }.add(
                            ApiMember(parts[2], MemberKind.FIELD, 0, parts[3]),
                        )
                    }
                }
            }

            val resolved = classes.mapValues { (fqn, cls) ->
                cls.copy(members = members[fqn].orEmpty())
            }
            // A second index keyed on the member's own name, because a call is written against a
            // method name and nothing about `setText(` says which class owns it. Built from the same
            // lists, so it cannot disagree with the per-class view.
            val byMember = LinkedHashMap<String, MutableList<ApiMember>>()
            for (list in members.values) {
                for (m in list.orEmpty()) {
                    byMember.getOrPut(m.name) { ArrayList() }.let {
                        if (!it.contains(m)) it.add(m)
                    }
                }
            }
            return AndroidApiIndex(
                resolved,
                simple.mapValues { it.value.toList() },
                byMember.mapValues { it.value.toList() },
            )
        }
    }
}