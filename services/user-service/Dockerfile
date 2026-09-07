# syntax=docker/dockerfile:1
#
# Multi-stage build. Build context is the REPOSITORY ROOT so the reactor parent POM and the
# shopflow-common module are available:
#     docker build -f services/user-service/Dockerfile -t shopflow/user-service .
# (docker-compose.yml wires this up in Phase 16.)

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY libs ./libs
COPY services ./services

# -am also builds shopflow-common; -pl restricts the reactor to this service.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -ntp -pl services/user-service -am -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/services/user-service/target/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
