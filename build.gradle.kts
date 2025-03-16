plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

allprojects {
    group = "com.dunctebot"

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://m2.duncte123.dev/releases")
        maven("https://m2.dv8tion.net/releases")
        maven("https://maven.lavalink.dev/releases")
        maven("https://maven.lavalink.dev/snapshots")
        maven("https://jitpack.io")
    }

    tasks.withType<Wrapper> {
        gradleVersion = "8.4"
        distributionType = Wrapper.DistributionType.BIN
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}


tasks.register("buildPlugin", Jar) {
    archiveClassifier.set("plugin")
    from(sourceSets.main.get().output)
}
