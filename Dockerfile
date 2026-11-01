# ETAPA 1: Compilación de Spring Boot (Build)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Descarga de dependencias en capa cacheada de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia de código fuente y empaquetado
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# ETAPA 2: Imagen Final de Producción (Run)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Seguridad: Ejecutar como usuario sin privilegios root
RUN addgroup -S atlasgroup && adduser -S atlasuser -G atlasgroup
USER atlasuser

# Copiar únicamente el archivo .jar generado
COPY --from=builder --chown=atlasuser:atlasgroup /app/target/*.jar app.jar

# Variables de entorno JVM recomendadas para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
=======
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
