rootProject.name = "FileSync"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }

    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        val composeVersion = extra["compose.version"] as String

        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("plugin.compose") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
    }
}

//pluginManagement {
//    repositories {
//        mavenCentral()
//        google()
//        gradlePluginPortal()
//        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
//        maven("https://www.jetbrains.com/intellij-repository/releases")
//        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
//    }
//}
//
//plugins {
//    id("org.jetbrains.amper.settings.plugin").version("0.3.1")
//}
