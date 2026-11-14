// NFR-08: install size. Run `./gradlew checkReleaseApkSize --offline`; fails when the release APK is 25 MB or more.
// The limit must equal ApkSizeBudget.MAX_BYTES in core-quality (a unit test compares the two).
val apkSizeLimitBytes = 25_000_000L

tasks.register("checkReleaseApkSize") {
    group = "verification"
    description = "Fails if the release APK is over the 25 MB install-size budget (NFR-08)."
    dependsOn("assembleRelease")
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val limit = apkSizeLimitBytes
    doLast {
        val apks = apkDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        check(apks.isNotEmpty()) { "No release APK found under ${apkDir.get().asFile}" }
        apks.forEach { apk ->
            val mb = apk.length() / 1_000_000.0
            println("NFR-08 ${apk.name}: %.2f MB (limit %.0f MB)".format(mb, limit / 1_000_000.0))
            if (apk.length() >= limit) throw GradleException("NFR-08: ${apk.name} is %.2f MB, over the 25 MB budget".format(mb))
        }
    }
}
