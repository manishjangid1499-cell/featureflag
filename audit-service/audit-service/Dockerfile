# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre AS runtime

RUN groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --no-create-home --home-dir /app --shell /usr/sbin/nologin appuser

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar /app/app.jar

USER appuser:appgroup

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=docker"]
