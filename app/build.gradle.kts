plugins {
    id("application")
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("io.netty:netty-transport:4.1.+")
    implementation("io.netty:netty-handler:4.1.+")
    implementation("io.netty:netty-codec-http:4.1.+")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.13")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("org.handler.ReverseProxy")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.handler.ReverseProxy"
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("app")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.handler.ReverseProxy"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

