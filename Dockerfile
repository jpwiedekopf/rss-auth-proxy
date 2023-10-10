FROM gradle:8.4.0-jdk17 AS builder
COPY --chown=gradle:gradle src build.gradle.kts settings.gradle.kts gradle.properties /home/gradle/src/
WORKDIR /home/gradle/src
RUN find . -type f -exec realpath {} \;
RUN gradle buildFatJar --no-daemon
FROM azul/zulu-openjdk-alpine:17-jre
COPY --from=builder /home/gradle/src/build/libs/*.jar /app/app.jar
EXPOSE 8123
VOLUME /config/
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
