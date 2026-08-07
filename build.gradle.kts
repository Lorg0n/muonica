import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.Copy

plugins {
    id("org.springframework.boot") version "4.1.0" apply false
}

allprojects {
    group = "io.muonica"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("java") {
        val moduleName = "${project.group}.${project.name.removePrefix("muonica-").replace('-', '.')}"

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        tasks.withType<Jar>().configureEach {
            manifest.attributes["Automatic-Module-Name"] = moduleName
        }
    }
}

val libraryProjects = listOf(
    ":muonica-core",
    ":muonica-openapi",
    ":muonica-ui",
    ":muonica-spring"
)

tasks.register<Copy>("buildLibraries") {
    group = "build"
    description = "Tests Muonica libraries and copies their JARs to the root build/libs directory."
    dependsOn(libraryProjects.map { "$it:check" })
    from(libraryProjects.map { project(it).tasks.named<Jar>("jar") })
    into(layout.buildDirectory.dir("libs"))
}
