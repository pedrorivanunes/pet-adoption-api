# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependências numa camada própria, antes do código-fonte: alterar uma classe
# não invalida o cache e não força baixar o Maven inteiro de novo.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Testes são pulados aqui de propósito: a suíte usa Testcontainers, que precisa
# de um Docker daemon -- indisponível dentro do build da imagem. Quem roda os
# testes é o CI (e a sua máquina), antes da imagem ser construída.
RUN ./mvnw -B -ntp clean package -DskipTests

# -------------------------------------------------------------- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Imagem final leva só o JRE, não o JDK, e roda como usuário sem privilégios.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
