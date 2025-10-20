plugins {
    application
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-transport:4.1.109.Final")
    implementation("io.netty:netty-handler:4.1.118.Final")
    implementation("io.netty:netty-codec-http:4.1.109.Final")
    implementation("io.netty:netty-buffer:4.1.109.Final")
    implementation("io.netty:netty-common:4.1.118.Final")
}

application {
    mainClass.set("org.testserver.TestServer")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.testserver.TestServer"
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("testserver")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.testserver.TestServer"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

