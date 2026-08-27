# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/user-service/src/ services/user-service/src/

RUN --mount=type=cache,id=ecommerce-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw \
    && ./mvnw -f services/user-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring \
    /workspace/services/user-service/target/user-service-*.jar \
    /app/application.jar

USER spring:spring
EXPOSE 8082

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --output-document=- http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
