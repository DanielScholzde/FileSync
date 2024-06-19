group = "de.danielscholz"
version = "0.1-SNAPSHOT"
description = "File sync"

val kotlinVersion = "2.0.0"

plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

application {
    mainClass.set("de.danielscholz.fileSync.MainKt")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("de.danielscholz:KArgParser:0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    implementation("com.google.guava:guava:33.2.0-jre") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
        exclude(group = "org.checkerframework", module = "checker-qual")
        exclude(group = "com.google.guava", module = "listenablefuture")
    }

//   testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
//   testImplementation("org.hamcrest:hamcrest:2.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

