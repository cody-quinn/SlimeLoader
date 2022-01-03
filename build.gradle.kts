import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"

    `maven-publish`
}

group = "us.phoenixnetwork"
version = "1.0.0-1.18-SNAPSHOT"

repositories {
    mavenCentral()

    maven(url = "https://repo.spongepowered.org/maven")
    maven(url = "https://jitpack.io")
}

dependencies {
    compileOnly("com.github.Minestom:Minestom:1.18-SNAPSHOT")

    api("com.github.luben:zstd-jni:1.5.0-4")
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
    jvmTarget = "17"
}

publishing {
    repositories {
        maven("${project.rootDir}/releases")
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            pom {
                packaging = "jar"
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/PhoenixNetwork/SlimeLoader/blob/master/LICENSE")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            val mavenUsername = project.properties["mavenUser"]
            val mavenPassword = project.properties["mavenPassword"]

            credentials {
                username = mavenUsername.toString()
                password = mavenPassword.toString()
            }

            val snapshotRepository = uri("https://repo.phoenixnetwork.us/repository/maven-snapshots/")
            val releaseRepository = uri("https://repo.phoenixnetwork.us/repository/maven-releases/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotRepository else releaseRepository
        }
    }
}
