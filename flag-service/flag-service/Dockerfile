# ==========================
# Stage 1 - Build the project
# ==========================
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# ==========================
# Stage 2 - Run the application
# ==========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the generated JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose application port
EXPOSE 8082

# Start the application using the Docker profile
ENTRYPOINT ["java","-jar","app.jar","--spring.profiles.active=docker"]