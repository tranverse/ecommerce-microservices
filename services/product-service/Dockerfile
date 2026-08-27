# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/product-service/pom.xml services/product-service/pom.xml
COPY services/product-service/src/ services/product-service/src/

RUN --mount=type=cache,id=ecommerce-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw \
    && ./mvnw -f services/product-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring \
    /workspace/services/product-service/target/product-service-*.jar \
    /app/application.jar

USER spring:spring
EXPOSE 8083

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --output-document=- http://localhost:8083/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
