import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    `java-test-fixtures`
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.blockchainhandler"
version = "1.0.0-SNAPSHOT"
description = "Ethereum event aggregator: USDC Transfer ingestion, enrichment, storage and REST API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Versions of libraries that are not managed by the Spring Boot BOM.
val web3jVersion = "4.14.0"                  // 5.x+ moved to Jackson 3, Spring Boot 3.x is on Jackson 2
val mapstructVersion = "1.6.3"
val lombokMapstructBindingVersion = "0.2.0"
val logstashLogbackEncoderVersion = "8.1"    // 9.x moved to Jackson 3
val springdocVersion = "2.9.1"               // built against Spring Boot 3.5.16
val mockWebServerVersion = "4.12.0"          // matches the OkHttp version used by web3j

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.liquibase:liquibase-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("org.web3j:core:$web3jVersion") {
        // AWS KMS signer support is not used and drags in the whole AWS SDK.
        exclude(group = "software.amazon.awssdk")
    }
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Shared test data (a real mainnet USDC Transfer log and builders), used by unit and integration tests.
    testFixturesImplementation("org.web3j:core:$web3jVersion") {
        exclude(group = "software.amazon.awssdk")
    }
    testFixturesImplementation("jakarta.validation:jakarta.validation-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Without an explicit version, test suites add Gradle's default JUnit (5.8.2), which would override the Boot BOM.
val junitJupiterVersion: String = dependencyManagement.importedProperties.getValue("junit-jupiter.version")

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(junitJupiterVersion)
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(junitJupiterVersion)
            dependencies {
                implementation(project())
                implementation(testFixtures(project()))
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.springframework.kafka:spring-kafka-test")
                implementation("org.testcontainers:junit-jupiter")
                implementation("org.testcontainers:postgresql")
                implementation("org.testcontainers:kafka")
                implementation("org.awaitility:awaitility")
                implementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

// Integration tests compile against everything the application and the unit tests use.
val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Amapstruct.defaultComponentModel=spring",
            "-Amapstruct.unmappedTargetPolicy=ERROR",
        ),
    )
}

tasks.withType<Test>().configureEach {
    // Mockito attaches its inline mock maker as an agent; make that explicit on JDK 21+.
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

springBoot {
    buildInfo {
        excludes = setOf("time")
    }
}

tasks.bootJar {
    archiveFileName = "blockchain-handler.jar"
}
