# syntax=docker/dockerfile:1
# Build context is the repository root. See services/user-service/Dockerfile for notes.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY services ./services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -ntp -pl services/payment-service -am -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/services/payment-service/target/*.jar app.jar
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app.jar"]
