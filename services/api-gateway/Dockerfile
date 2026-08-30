# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/api-gateway/src/ services/api-gateway/src/

RUN --mount=type=cache,id=ecommerce-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw \
    && ./mvnw -f services/api-gateway/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring \
    /workspace/services/api-gateway/target/api-gateway-*.jar \
    /app/application.jar

USER spring:spring
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --output-document=- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
