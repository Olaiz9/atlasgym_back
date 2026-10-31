# ============================================================================
# ATLAS Gym Backend - Multi-Stage Production Dockerfile (Java 17 / Spring Boot)
# ============================================================================

FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine AS runner
RUN apk add --no-cache tzdata curl && \
    addgroup -S atlas && \
    adduser -S atlas -G atlas

WORKDIR /app
ENV TZ=America/Argentina/Buenos_Aires
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Argentina/Buenos_Aires"

COPY --from=builder --chown=atlas:atlas /build/target/*.jar app.jar

USER atlas
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
