group = "de.danielscholz"
version = "0.1-SNAPSHOT"
description = "File sync"

val kotlinVersion = "2.0.0"
val coroutinesVersion = "1.8.1"

plugins {
//   java
    application
    kotlin("jvm") version "2.0.0"
//   id("org.jetbrains.compose") version "1.6.10"
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
//   implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
//   implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-core:1.2.11")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("com.google.guava:guava:31.1-jre") {
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
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

