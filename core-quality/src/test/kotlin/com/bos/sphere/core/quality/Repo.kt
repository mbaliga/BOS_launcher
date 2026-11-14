package com.bos.sphere.core.quality

import java.io.File

/** Locates the repository root from a unit test's working directory (the module folder). */
object Repo {
    val root: File by lazy {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) d = d.parentFile
        requireNotNull(d) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }

    fun file(path: String) = File(root, path)

    /** Production Kotlin sources of every module (src/main only). */
    fun mainSources(): List<File> = root.listFiles { f -> f.isDirectory && File(f, "src/main").exists() }.orEmpty()
        .flatMap { File(it, "src/main").walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
}
