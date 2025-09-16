plugins {
    application
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
    runtimeOnly("org.slf4j:slf4j-simple:2.0.+")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "org.handler.ReverseProxy"
}
