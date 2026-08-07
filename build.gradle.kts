import org.gradle.jvm.tasks.Jar

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
