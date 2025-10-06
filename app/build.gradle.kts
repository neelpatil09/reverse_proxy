import org.gradle.api.tasks.JavaExec

plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)

    implementation(libs.guava)

    implementation("io.netty:netty-transport:4.1.+")
    implementation("io.netty:netty-handler:4.1.+")
    implementation("io.netty:netty-codec-http:4.1.+")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

}

application {
    mainClass.set("org.handler.ReverseProxy")
}

tasks {

    //
    // === BUILD JARS ===
    //

    jar {
        archiveBaseName.set("reverse-proxy")
        manifest.attributes["Main-Class"] = "org.handler.ReverseProxy"
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith("jar") }
                .map { zipTree(it) }
        })
    }

    register<Jar>("testServerJar") {
        group = "build"
        description = "Build executable jar for Netty test servers"
        archiveBaseName.set("test-servers")
        manifest.attributes["Main-Class"] = "org.testserver.TestServer"
        from(sourceSets.main.get().output)
        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith("jar") }
                .map { zipTree(it) }
        })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    register("buildAll") {
        group = "build"
        dependsOn("jar", "testServerJar")
    }

    //
    // === RUN LOCALLY ===
    //

    named<JavaExec>("run") {
        mainClass.set("org.handler.ReverseProxy")
        classpath = sourceSets.main.get().runtimeClasspath
    }

    register<JavaExec>("run_test") {
        group = "application"
        description = "Run Netty test servers locally"
        mainClass.set("org.testserver.TestServer")
        classpath = sourceSets.main.get().runtimeClasspath
    }

    //
    // === DOCKER BUILDS ===
    //

    register<Exec>("dockerBuildProxy") {
        group = "docker"
        description = "Build reverse-proxy Docker image"
        dependsOn("buildAll")
        commandLine("docker", "build", "-f", "../Dockerfile.proxy", "-t", "reverse-proxy", "..")
    }

    register<Exec>("dockerBuildTest") {
        group = "docker"
        description = "Build test-servers Docker image"
        dependsOn("buildAll")
        commandLine("docker", "build", "-f", "../Dockerfile.test", "-t", "test-servers", "..")
    }

    register("dockerBuildAll") {
        group = "docker"
        description = "Build both Docker images"
        dependsOn("dockerBuildProxy", "dockerBuildTest")
    }

    register("buildAllDocker") {
        group = "docker"
        description = "Build JARs and Docker images together"
        dependsOn("dockerBuildAll")
    }
}
