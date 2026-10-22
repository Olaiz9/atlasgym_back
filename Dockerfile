# ==========================================
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
