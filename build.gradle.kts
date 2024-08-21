import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.javamodularity.moduleplugin.extensions.TestModuleOptions
import org.gradle.internal.os.OperatingSystem


plugins {
    val kotlinVersion = "1.7.10"
    application

    jacoco
    idea
    antlr
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    id("org.sonarqube") version "5.1.0.4882"
    id("com.github.ben-manes.versions") version "0.36.0"

    id("org.openjfx.javafxplugin") version "0.0.13"

    id("org.javamodularity.moduleplugin") version "1.8.12"
    id("org.beryx.jlink") version "2.25.0"

    id("com.palantir.git-version") version "2.0.0"
}

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/spekframework/spek/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://maven.atlassian.com/content/repositories/atlassian-public/")
}

// -SNAPSHOT is added if the release task is not set
// jpackge for windows needs an 2 digit version, at least
val upcomingVersion = "4.0"
val archivesBaseName = "STT"

version = upcomingVersion

application {
    mainModule.set("org.stt")
    mainClass.set("org.stt.StartWithJFX")
    // add-opens, so we can access the file decoration-warning.png within this module
    applicationDefaultJvmArgs =
        listOf("--add-opens=org.controlsfx.controls/impl.org.controlsfx.control.validation=org.stt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

kapt {
    correctErrorTypes = true
}

configurations {
    implementation {
        setExtendsFrom(extendsFrom.filter { it != configurations.antlr })
    }
}

val spek_version = "2.0.4"

dependencies {
    val daggerVersion = "2.43.1"
    antlr(group = "org.antlr", name = "antlr4", version = "4.9.1")
    implementation(group = "org.antlr", name = "antlr4-runtime", version = "4.9.1")

    implementation(group = "org.fxmisc.richtext", name = "richtextfx", version = "0.11.0") {
        exclude(group = "org.openjfx")
    }
    implementation("org.yaml:snakeyaml:1.27")
    implementation("com.google.dagger:dagger:$daggerVersion")
    implementation("javax.inject:javax.inject:1")
    kapt("com.google.dagger:dagger-compiler:$daggerVersion")
    implementation("net.engio:mbassador:1.3.2")
    implementation("org.controlsfx:controlsfx:11.1.2")
    implementation("com.jsoniter:jsoniter:0.9.23")
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("commons-io:commons-io:2.8.0")
    testImplementation("org.mockito:mockito-core:4.5.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.assertj:assertj-core:3.18.1")
    testImplementation("junit:junit-dep:4.11")
}

javafx {
    version = "17.0.1"
    modules("javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics")
}

sonar {
    properties {
        property("sonar.projectkey", "org.stt:stt")
        property("sonar.projectName", "SimpleTimeTracking")
    }
}

distributions.getByName("main") {
    contents {
        include("**/STT*")
    }
}

tasks.compileJava {
    // workaround, to make kapt created classes available to java module source set
    sourceSets {
        main {
            java {
                srcDir("$buildDir/generated/source/kapt/main")
            }
        }
    }
}

tasks.test {
    extensions.configure(TestModuleOptions::class) {
        // disable java-module path for tests
        runOnClasspath = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask> {
    dependsOn(tasks.withType<AntlrTask>())
}
// provided by plugin: com.palantir.git-version
val gitVersion: groovy.lang.Closure<String> by project.extra
val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra

tasks.withType<ProcessResources> {
    filesMatching("version.info") {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "app.version" to project.version,
                "app.hash" to versionDetails().gitHash
            )
        )
    }
    doLast {
        println("Written tokes into file version.info")
    }
}

task("dist") {
    dependsOn += "jpackage"
    dependsOn += "jlinkZip"
}

task("release") {
    dependsOn += "dist"
    doLast {
        println("Built release for $project.version")
    }
}

gradle.taskGraph.whenReady {
    if (!hasTask("release")) {
        version = (upcomingVersion as String) + "-SNAPSHOT"
    }
}

tasks.withType<AntlrTask> {
    maxHeapSize = "64m"
    arguments = arguments + "-visitor" + "-long-messages"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

//tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
//    val badVersions = ".*(-rc(-.*)?$|-m.*)".toRegex()
//    rejectVersionIf {
//        candidate.version.toLowerCase().matches(badVersions)
//    }
//}

jlink {
    imageZip.set(File("$buildDir/dist/zips/stt-${javafx.platform.classifier}-${version}.zip"))
    addOptions("--bind-services", "--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
    mergedModule {
        excludeRequires("javafx.graphics", "javafx.controls", "javafx.base")
    }
    forceMerge("kotlin") // see https://stackoverflow.com/questions/74453018/jlink-package-kotlin-in-both-merged-module-and-kotlin-stdlib
    launcher {
        name = "stt"
        jvmArgs =
            application.applicationDefaultJvmArgs.plus(
                listOf(
                    // some classes in the merged-modules (see jlink plugin( need access to javafx modules
                    "--add-reads=simpleTimeTracking.merged.module=javafx.graphics",
                    "--add-reads=simpleTimeTracking.merged.module=javafx.base",
                    "--add-reads=simpleTimeTracking.merged.module=javafx.controls"
                )
            )
    }
    jpackage {
        installerOutputDir = File( "$buildDir/dist/installer/${javafx.platform.classifier}")
        skipInstaller = false
        appVersion = upcomingVersion

        val os = OperatingSystem.current()
        println("Building on ${os.toString()}.")

        if (os.isLinux || os.isUnix) {
            icon = "src/main/resources/Logo.png"
            installerOptions.plus(listOf(
                "--linux-menu-group", "Office",
                "--linux-shortcut"))
        }
        if (os.isWindows) {
            installerType = "exe"
            icon = "src/main/resources/Logo.ico"
            installerOptions.plus(listOf(
                "--win-upgrade-uuid", "0e521a7f-2fe2-4d30-9065-8a6972e11b56",
                "--win-shortcut"))
        }
        if (os.isMacOsX) {
            installerType = "dmg"
            icon = "src/main/resources/Logo.icns"
        }
    }
}

