package com.bos.sphere.feature.dock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Plain JSON file in app storage, written through a temporary file and a rename so a crash never leaves half a document.
 *
 * NFR-18: the per-workspace widget lists hold app widget ids, which mean nothing on another install. With a [deviceFile]
 * (the app passes one in `noBackupFilesDir`, which backup and device transfer skip) the lists are written there, keyed by
 * workspace id, and the main file keeps an empty `widgets` array (read as "never saved", so a restored file changes no
 * widgets). A main file written by an older build, which still holds the lists, is moved across the first time it is read.
 * [read] puts the lists back, so the manager sees the same document as before.
 */
class FileWorkspaceStore(private val file: File, private val deviceFile: File? = null) : WorkspaceStore {
    override fun read(): String? {
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrNull() ?: return null
        if (deviceFile == null) return text
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return text
        val ws = o.optJSONArray("workspaces") ?: return text
        val side = readSide()
        var legacy = false
        for (i in 0 until ws.length()) {
            val w = ws.optJSONObject(i) ?: continue
            val own = w.optJSONArray("widgets")
            if (own != null && own.length() > 0) { side.put(w.optString("id"), own); legacy = true }
            else side.optJSONArray(w.optString("id"))?.let { w.put("widgets", it) }
        }
        if (legacy) { writeSide(o); writeMain(stripped(o).toString()) }
        return o.toString()
    }

    override fun write(text: String) {
        val o = deviceFile?.let { runCatching { JSONObject(text) }.getOrNull() }
        if (o == null || deviceFile == null) { writeMain(text); return }
        writeSide(o)
        writeMain(stripped(o).toString())
    }

    private fun readSide(): JSONObject = runCatching { JSONObject(deviceFile!!.readText()) }.getOrDefault(JSONObject())

    /** Replaces the side file with the lists of the workspaces in [doc] (a removed workspace drops its list). */
    private fun writeSide(doc: JSONObject) {
        val side = JSONObject()
        val ws = doc.optJSONArray("workspaces")
        for (i in 0 until (ws?.length() ?: 0)) {
            val w = ws!!.optJSONObject(i) ?: continue
            val a = w.optJSONArray("widgets")
            if (a != null && a.length() > 0) side.put(w.optString("id"), a)
        }
        runCatching {
            deviceFile!!.parentFile?.mkdirs()
            val tmp = File(deviceFile.parentFile, deviceFile.name + ".tmp")
            tmp.writeText(side.toString())
            if (!tmp.renameTo(deviceFile)) { deviceFile.writeText(side.toString()); tmp.delete() }
        }
    }

    private fun stripped(doc: JSONObject): JSONObject {
        val copy = JSONObject(doc.toString())
        val ws = copy.optJSONArray("workspaces") ?: return copy
        for (i in 0 until ws.length()) ws.optJSONObject(i)?.put("widgets", JSONArray())
        return copy
    }

    private fun writeMain(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
    }
}

/** One manager per process, reloaded from the file after process death. */
object WorkspaceRepository {
    @Volatile private var instance: WorkspaceManager? = null
    /** What stands in while the launcher screen is not composed: a switch is held until real hooks attach (WSP-12). */
    val DETACHED: WorkspaceHooks = object : WorkspaceHooks { override val ready: Boolean get() = false }
    @Volatile var hooks: WorkspaceHooks = DETACHED
    /** Wave 57 (CFG-48): where a profile's carried settings are read and written; the stores of the app unless a test sets another. */
    @Volatile var settings: ProfileSettingsPort? = null

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "dock-store").apply { isDaemon = true } }

    fun get(context: Context): WorkspaceManager = instance ?: synchronized(this) {
        instance ?: WorkspaceManager(
            FileWorkspaceStore(File(context.applicationContext.filesDir, "liftoff_workspaces.json"), File(context.applicationContext.noBackupFilesDir, "liftoff_workspace_widgets.json")),
            hooks = { hooks }, io = { writer.execute(it) },
            settingsPort = { settings ?: StoresProfileSettings(context.applicationContext).also { settings = it } },
        ).also { instance = it }
    }
}
