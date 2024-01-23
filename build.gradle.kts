plugins {
    kotlin("jvm") version "1.9.21"
}

group = "dev.remadisson"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("com.google.code.gson", "gson", "2.7")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Jar>() {
    manifest {
        attributes["Main-Class"] = "dev.remadisson.MainKt"
    }
    configurations["compileClasspath"].filter { it.name == "gson-2.7.jar" || it.name == "kotlin-stdlib-1.9.21.jar" }.map { file: File ->
        from(zipTree(file.absoluteFile))
    }
}

tasks.test {
    useJUnitPlatform()
}

