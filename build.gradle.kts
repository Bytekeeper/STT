import com.github.spotbugs.SpotBugsTask
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.internal.KaptWithKotlincTask
import org.sonarqube.gradle.SonarQubeTask
import sun.tools.jar.resources.jar
import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    val kotlinVersion = "1.2.51"
    application
    jacoco
    idea
    antlr
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    id("org.sonarqube") version "2.6.2"
    id("com.github.ben-manes.versions") version "0.20.0"
    id("com.github.spotbugs") version "1.6.2"
}

repositories {
    mavenCentral()
    maven {
        url = URI("http://oss.sonatype.org/content/groups/public/")
    }
    maven {
        url = URI("http://maven.atlassian.com/content/repositories/atlassian-public/")
    }
}


// -SNAPSHOT is added if the release task is not set
version = "3"
val archivesBaseName = "STT"

application {
    mainClassName = "org.stt.StartWithJFX"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

spotbugs {
    excludeFilter = file("config/findbugs/excludeFilter.xml")
}

tasks.withType<SpotBugsTask> {
    reports {
        xml.setEnabled(false)
        html.setEnabled(true)
    }
}

configurations {
    "compile" {
        setExtendsFrom(extendsFrom.filter { it != configurations.antlr })
    }
}

dependencies {
    antlr(group = "org.antlr", name = "antlr4", version = "4.7.1")
    compile(group = "org.antlr", name = "antlr4-runtime", version = "4.7.1")
    compile(group = "org.fxmisc.richtext", name = "richtextfx", version = "0.9.0")
    compile("org.yaml:snakeyaml:1.21")
    compile("com.google.dagger:dagger:2.16")
    compile("javax.inject:javax.inject:1")
    kapt("com.google.dagger:dagger-compiler:2.16")
    compile("net.engio:mbassador:1.3.2")
    compile("org.controlsfx:controlsfx:8.40.14")
    compile("net.rcarz:jira-client:0.5")
    compile("com.jsoniter:jsoniter:0.9.23")
    compile(kotlin("stdlib-jdk8"))

    testCompile("commons-io:commons-io:2.6")
    testCompile("junit:junit-dep:4.11")
    testCompile("org.hamcrest:hamcrest-core:1.3")
    testCompile("org.hamcrest:hamcrest-library:1.3")
    testCompile("org.mockito:mockito-core:2.19.1")
}

distributions.getByName("main") {
    contents {
        include("**/STT*")
    }
}

tasks.withType<Jar> {
    from(configurations.compile.resolve().map { if (it.isDirectory()) it else zipTree(it) })
    manifest {
        attributes += "Main-Class" to "org.stt.StartWithJFX"
        attributes += "JavaFX-Feature-Proxy" to "None"
    }
}

tasks.withType<KaptTask> { dependsOn(tasks.withType<AntlrTask>()) }

tasks.withType<ProcessResources> {
    filesMatching("version.info") {
        filter<ReplaceTokens>("tokens" to mapOf(
                "app.version" to project.property("version"),
                "app.hash" to getCheckedOutGitCommitHash()
        ))
    }
}

tasks.withType<FindBugs> {
    reports {
        xml.setEnabled(false)
        html.setEnabled(true)
    }
}


tasks.withType<Wrapper> {
    gradleVersion = "4.8.1"
}

task("release") {
    dependsOn += "distZip"
    doLast {
        println("Built release for $project.version")
    }
}

gradle.taskGraph.whenReady {
    if (!hasTask("release")) {
        version = (version as String) + "-SNAPSHOT"
    }
}

tasks.withType<AntlrTask> {
    maxHeapSize = "64m"
    arguments = arguments + "-visitor" + "-long-messages"
}


fun getCheckedOutGitCommitHash(): String {
    val takeFromHash = 12
    /*
     * '.git/HEAD' contains either
     *      in case of detached head: the currently checked out commit hash
     *      otherwise: a reference to a file containing the current commit hash
     */
    val head = file(".git/HEAD").readText().split(":") // .git/HEAD
    val isCommit = head.size == 1 // e5a7c79edabbf7dd39888442df081b1c9d8e88fd

    if (isCommit) return head[0].trim().take(takeFromHash) // e5a7c79edabb

    val refHead = file(".git/logs/" + head[1].trim()) // .git/refs/heads/master
    return refHead.readText().trim().take(takeFromHash)
}

tasks.withType<SonarQubeTask> {
    properties += "sonar.projectName" to "SimpleTimeTracking"
    properties += "sonar.projectKey" to "org.stt:stt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
