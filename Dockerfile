# syntax=docker/dockerfile:1

# ---- Build: compile the Spring Boot jar and split it into layers -------------------------------------------------
FROM gradle:8.14.5-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties lombok.config ./
COPY src/main src/main
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon --quiet \
    && java -Djarmode=tools -jar build/libs/blockchain-handler.jar extract --layers --launcher --destination build/extracted

# ---- Runtime: JRE only, non-root user, dependencies in their own (rarely changing) layers -------------------------
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
