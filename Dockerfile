FROM gradle:8.4.0-jdk17 AS builder
WORKDIR /home/gradle/src
ADD --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./
ADD --chown=gradle:gradle src ./src
RUN gradle buildFatJar --no-daemon
FROM azul/zulu-openjdk-alpine:17-jre
COPY --from=builder /home/gradle/src/build/libs/rss-auth-proxy-all.jar /app/app.jar
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
