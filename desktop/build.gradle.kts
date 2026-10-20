import org.jetbrains.compose.desktop.application.dsl.TargetFormat

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17

        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

val nrgVersion = project.property("narcicgeteway.versionName") as String

val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val version = nrgVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.resolve("dev/valid7996/narcicgeteway/desktop")
        dir.mkdirs()
        dir.resolve("BuildVersion.kt").writeText(
            "package dev.valid7996.narcicgeteway.desktop\n\ninternal const val BUILD_VERSION = \"$version\"\n",
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateVersion)
}

configurations.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3.mp)
    implementation(libs.compose.components.resources)
    implementation(libs.lifecycle.viewmodel.compose.mp)
    implementation(libs.lifecycle.runtime.compose.mp)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.compose.native.tray)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val desktopPackageVersion = when (nrgVersion.count { it == '.' }) {
    0 -> "$nrgVersion.0.0"
    1 -> "$nrgVersion.0"
    else -> nrgVersion
}

compose.desktop {
    application {
        mainClass = "dev.valid7996.narcicgeteway.desktop.GuiKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Narcic Getway"
            packageVersion = desktopPackageVersion
            description = "Narcic Getway VPN client"
            vendor = "valid7996"
            linux {
                iconFile.set(project.file("icons/narcicgeteway.png"))
                packageName = "narcicgetway"
                menuGroup = "Network"
                shortcut = true
            }
            windows {
                iconFile.set(project.file("icons/narcicgeteway.ico"))
                menuGroup = "Narcic Getway"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/narcicgeteway.icns"))
                bundleID = "dev.valid7996.narcicgeteway"
                minimumSystemVersion = "12.0"
            }
        }
    }
}
