# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --configuration runtimeClasspath --no-daemon
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 10001 kursplatform \
    && useradd --system --uid 10001 --gid kursplatform --home-dir /app --shell /usr/sbin/nologin kursplatform
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/libs/*-SNAPSHOT.jar /app/application.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/application.jar"]
