plugins {
    java
    `maven-publish`
}

group = "net.codeverse"
version = "0.1.4"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // Published so consumers get parameter names and documentation in their
    // editor. A library nobody can read from the IDE is one used incorrectly.
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Targets 21 rather than 25 so consumers are not forced onto the same JDK
    // the plugins depending on this happen to use. Matches CodeverseAPI.
    options.release.set(21)
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.withType<Test> {
    useJUnitPlatform()
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "updater"
            pom {
                name.set("Codeverse Updater")
                description.set("A small library for staging plugin updates from GitHub releases.")
                url.set("https://github.com/CodeVerseHub-Minecraft/CodeverseUpdater")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("codeversehub-minecraft")
                        name.set("CodeVerseHub-Minecraft Subteam")
                    }
                }
            }
        }
    }
}
