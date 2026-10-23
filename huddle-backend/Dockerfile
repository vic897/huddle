# ==============================================================================
# Huddle Backend Dockerfile for Render (Free Web Service)
# Multi-stage Maven build with Eclipse Temurin Java 17
# Memory-conscious runtime tailored for 512 MB RAM container
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build the fat JAR
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Cache dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build production package
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal runtime image
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root system user and group for container security
RUN addgroup -S huddle && adduser -S huddle -G huddle

# Copy the packaged Spring Boot JAR from builder stage
COPY --from=builder /app/target/backend-*.jar app.jar

# Set file ownership to non-root user
RUN chown -R huddle:huddle /app

# Switch to non-root user
USER huddle:huddle

# Render sets PORT dynamically (default to 8080 for local testing)
ENV PORT=8080
EXPOSE 8080

# Memory-conscious JVM options for Render free instance (512 MB RAM limit):
# - XX:+UseContainerSupport: respects Linux cgroup memory constraints
# - XX:MaxRAMPercentage=70.0: caps heap at ~358 MB, leaving ~154 MB for OS/Metaspace/threads
# - XX:+UseSerialGC: significantly lower memory overhead than G1GC for low-core/low-RAM containers
# - XX:+ExitOnOutOfMemoryError: guarantees fast, clean restart on memory exhaustion
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Start application:
# - exec replaces shell as PID 1 to handle SIGTERM signals cleanly
# - -Dserver.port dynamically binds to Render's $PORT
# - -Djava.security.egd improves startup time by using non-blocking entropy source
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
