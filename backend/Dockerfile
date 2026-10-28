# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xms128m -Xmx384m"

RUN groupadd --system app && useradd --system --gid app app

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
