package com.bos.sphere.feature.dock

import android.content.Context
import java.io.File

/** Plain JSON file in app storage, written through a temporary file and a rename so a crash never leaves half a document. */
class FileWorkspaceStore(private val file: File) : WorkspaceStore {
    override fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
    }
}

/** One manager per process, reloaded from the file after process death. */
object WorkspaceRepository {
    @Volatile private var instance: WorkspaceManager? = null
    @Volatile var hooks: WorkspaceHooks = object : WorkspaceHooks {}
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "dock-store").apply { isDaemon = true } }

    fun get(context: Context): WorkspaceManager = instance ?: synchronized(this) {
        instance ?: WorkspaceManager(
            FileWorkspaceStore(File(context.applicationContext.filesDir, "liftoff_workspaces.json")),
            hooks = { hooks }, io = { writer.execute(it) },
        ).also { instance = it }
    }
}
