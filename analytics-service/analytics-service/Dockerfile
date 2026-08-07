# ==========================
# Stage 1 - Build the project
# ==========================
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

COPY src ./src

RUN mvn clean package -DskipTests

# ==========================
# Stage 2 - Run the application
# ==========================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=docker"]