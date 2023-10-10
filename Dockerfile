FROM gradle:8.4.0-jdk17 AS builder
COPY --chown=gradle:gradle . /home/gradle/src/
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon
FROM azul/zulu-openjdk-alpine:17-jre
COPY --from=builder /home/gradle/src/build/libs/rss-auth-proxy-all.jar /app/app.jar
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
