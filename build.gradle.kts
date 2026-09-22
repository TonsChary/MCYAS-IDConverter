plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.pauleff"
version = "26.1"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("org.json:json:20251224")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("com.github.Querz:NBT:6.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

application {
    mainClass.set("me.pauleff.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    // Build with the current toolchain but target Java 17 so the artifact runs on
    // the JDK shipped with 1.18+ servers instead of requiring Java 26.
    options.release.set(17)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("html5", true)
        windowTitle("MCYAS-IDConverter ${project.version} API")
        docTitle("MCYAS-IDConverter ${project.version}")
    }
}

tasks.jar {
    archiveBaseName.set("MCYAS-IDConverter")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("thin")
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version.toString(),
                "Specification-Title" to project.name,
                "Specification-Version" to project.version.toString(),
            ),
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("MCYAS-IDConverter")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "me.pauleff.Main",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version.toString(),
                "Specification-Title" to project.name,
                "Specification-Version" to project.version.toString(),
            ),
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
