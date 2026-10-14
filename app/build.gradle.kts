import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

setupApp()

val verifyNativeCoreBindings = tasks.register("verifyNativeCoreBindings") {
    group = "verification"
    description = "Verify that the local core AAR exposes the app's profile-test bindings"
    val coreAar = layout.projectDirectory.file("libs/libexclavecore.aar").asFile
    inputs.files(coreAar)
    doLast {
        val rebuild = "Rebuild it from the repository root with ./run lib core " +
            "(or ./library/core/build.sh), then rerun Gradle."
        if (!coreAar.isFile) {
            throw GradleException("Native core AAR is missing: $coreAar. $rebuild")
        }
        val required = setOf("libexclavecore/ProfileProbe.class", "libexclavecore/ProfileCountry.class",
            "libexclavecore/ProfileProbe.urlTest")
        val bindings: Set<String> = try {
            ZipFile(coreAar).use { aar ->
                val classes = aar.getEntry("classes.jar")
                    ?: throw GradleException("Native core AAR has no classes.jar. $rebuild")
                ZipInputStream(aar.getInputStream(classes)).use { jar ->
                    buildSet<String> {
                        var entry = jar.nextEntry
                        while (entry != null) {
                            add(entry.name)
                            if (entry.name == "libexclavecore/ProfileProbe.class" &&
                                jar.readBytes().toString(Charsets.ISO_8859_1).contains("urlTest")) {
                                add("libexclavecore/ProfileProbe.urlTest")
                            }
                            entry = jar.nextEntry
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            throw GradleException("Cannot read native core AAR: $coreAar. $rebuild", e)
        }
        val missing = required - bindings
        if (missing.isNotEmpty()) {
            throw GradleException("Native core AAR is stale; missing ${missing.joinToString()}. $rebuild")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyNativeCoreBindings)
}

android {
    namespace = "io.nekohasekai.sagernet"
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("src/main/aboutlibraries/config")
        includePlatform = true
    }
    export {
        excludeFields.addAll("name", "description", "developers", "funding", "licenses", "organization", "scm", "website", "License")
        prettyPrint = true
    }
    exports {
        create("ossRelease") {
            outputFile = file("src/main/aboutlibraries/aboutlibraries.json")
        }
        create("legacyRelease") {
            outputFile = file("src/main/aboutlibraries/aboutlibraries_legacy.json")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(fileTree("libs"))
    implementation(project(":plugin:api"))
    implementation(project(":library:proto-stub"))
    implementation(libs.kotlinx.coroutines.android)
    "ossImplementation"(libs.core.ktx)
    "ossImplementation"(libs.activity.ktx)
    "ossImplementation"(libs.fragment.ktx)
    "ossImplementation"(libs.camera.view)
    "ossImplementation"(libs.camera.lifecycle)
    "ossImplementation"(libs.camera.camera2)
    implementation(libs.swiperefreshlayout)
    "ossImplementation"(libs.appcompat)
    implementation(libs.preference)
    implementation(libs.flexbox)
    "ossImplementation"(libs.work.runtime.ktx)
    "ossImplementation"(libs.work.multiprocess)
    "ossImplementation"(libs.room.runtime)
    "kspOss"(libs.room.compiler)
    "ossImplementation"(libs.room.ktx)
    "ossImplementation"(libs.material)
    implementation(libs.gson)
    implementation(libs.bouncycastle)
    implementation(libs.zxing.core)
    implementation(libs.snakeyaml)
    implementation(libs.material.about.library)
    implementation(libs.process.phoenix)
    implementation(libs.kryo)
    implementation(libs.jini.lib)
    implementation(libs.markwon.core)
    implementation(libs.recyclerview.fastscroll) {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }
    implementation(libs.editorkit)
    implementation(libs.editorkit.language.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    "legacyImplementation"(libs.core.ktx.minSdk21)
    "legacyImplementation"(libs.activity.ktx.minSdk21)
    "legacyImplementation"(libs.fragment.ktx.minSdk21)
    "legacyImplementation"(libs.camera.view.minSdk21)
    "legacyImplementation"(libs.camera.lifecycle.minSdk21)
    "legacyImplementation"(libs.camera.camera2.minSdk21)
    "legacyImplementation"(libs.appcompat.minSdk21)
    "legacyImplementation"(libs.work.runtime.ktx.minSdk21)
    "legacyImplementation"(libs.work.multiprocess.minSdk21)
    "legacyImplementation"(libs.room.runtime.minSdk21)
    "kspLegacy"(libs.room.compiler.minSdk21)
    "legacyImplementation"(libs.room.ktx.minSdk21)
    "legacyImplementation"(libs.material.minSdk21)
}
