package app.pebo.data

import okio.FileSystem
import okio.Path

/** Preference key for the user-chosen notes workspace folder (see the Storage settings). */
const val PREF_NOTES_DIR: String = "storage.notesDir"

/**
 * Tiny key/value preferences abstraction for app-level settings (theme, chosen folder, …).
 * Kept deliberately small and dependency-free so it works across every target.
 */
interface AppPreferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String)

    /** A no-op store so view models can be constructed in tests without touching disk. */
    object NoOp : AppPreferences {
        override fun getString(key: String): String? = null
        override fun putString(key: String, value: String) {}
    }
}

/**
 * A flat `key=value` text file (one entry per line). Robust enough for a handful of app settings and
 * trivially portable — no JSON parser or platform pref API required.
 */
class FileAppPreferences(
    private val fs: FileSystem,
    private val file: Path,
) : AppPreferences {
    private val cache = LinkedHashMap<String, String>()

    init {
        runCatching {
            if (fs.exists(file)) {
                fs.read(file) { readUtf8() }
                    .lineSequence()
                    .forEach { line ->
                        val idx = line.indexOf('=')
                        if (idx > 0) {
                            val k = line.substring(0, idx).trim()
                            val v = line.substring(idx + 1)
                            if (k.isNotEmpty()) cache[k] = decode(v)
                        }
                    }
            }
        }
    }

    override fun getString(key: String): String? = cache[key]

    override fun putString(key: String, value: String) {
        cache[key] = value
        runCatching {
            file.parent?.let { fs.createDirectories(it) }
            fs.write(file) {
                writeUtf8(cache.entries.joinToString("\n") { (k, v) -> "$k=${encode(v)}" })
            }
        }
    }

    private fun encode(v: String): String = v.replace("\\", "\\\\").replace("\n", "\\n")
    private fun decode(v: String): String = v.replace("\\n", "\n").replace("\\\\", "\\")
}
