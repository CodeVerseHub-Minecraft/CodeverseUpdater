plugins {
    java
    `maven-publish`
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "net.codeverse"
    version = "0.1.1"

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
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
        // Targets 21 rather than 25 so consumers are not forced onto the same
        // JDK the plugins depending on this happen to use. Matches CodeverseAPI.
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

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
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
}
