import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "de.danielscholz"
version = "0.1-SNAPSHOT"
description = "File sync"


plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

application {
    mainClass.set("de.danielscholz.fileSync.MainKt")
}

repositories {
    mavenCentral()
    google()
    mavenLocal()
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.desktop.currentOs)
    implementation(kotlin("reflect")) // only used by kargparser
    //implementation("de.danielscholz:KArgParser:0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC.2")
    implementation("com.google.guava:guava:33.2.0-jre") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
        exclude(group = "org.checkerframework", module = "checker-qual")
        exclude(group = "com.google.guava", module = "listenablefuture")
    }

//   testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
//   testImplementation("org.hamcrest:hamcrest:2.2")
}

//tasks.test {
//    useJUnitPlatform()
//}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21) // needed?
        //freeCompilerArgs.add("-Xcontext-receivers")
    }
}

