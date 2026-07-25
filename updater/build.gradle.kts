// The updater has no third party dependencies. It speaks to GitHub with the
// JDK's own HttpClient and parses JSON by hand, so a plugin adopting it takes
// on nothing it has to relocate or account for in its own notices. The whole
// library is a handful of classes with a small, testable surface.
base {
    archivesName.set("updater")
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "updater"
        pom {
            name.set("Codeverse Updater")
            description.set("A small library for staging plugin updates from GitHub releases.")
        }
    }
}

// Writes the project version where SelfUpdateVersionTest can read it, so a
// release cannot ship a library whose hardcoded self version has drifted from
// the build. The claim in SelfUpdate that a test guards this is made true here.
val writeVersion = tasks.register("writeVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/version")
    outputs.dir(outputDir)
    val v = version.toString()
    doLast {
        val file = outputDir.get().file("build-version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(v)
    }
}

tasks.named("processTestResources") {
    dependsOn(writeVersion)
}

sourceSets {
    test {
        resources {
            srcDir(layout.buildDirectory.dir("generated/version"))
        }
    }
}
