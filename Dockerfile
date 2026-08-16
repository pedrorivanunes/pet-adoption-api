# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependencies in a layer of their own, ahead of the source: changing a class
# does not invalidate the cache and does not force downloading Maven all over.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Tests are skipped here on purpose: the suite uses Testcontainers, which needs
# a Docker daemon -- not available inside an image build. CI (and your machine)
# is what runs the tests, before the image is built.
RUN ./mvnw -B -ntp clean package -DskipTests

# -------------------------------------------------------------- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# The final image carries only the JRE, not the JDK, and runs as an
# unprivileged user.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
