# =============================================================================
# AURORA Service Dockerfile
# =============================================================================
# Multi-stage build for optimized image size

# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy parent pom and install dependencies first (better caching)
COPY ../pom.xml /build/parent-pom.xml
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B -f pom.xml || true

# Copy source code
COPY src ./src
COPY schema ./schema

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="BUTTERFLY Team <butterfly@254studioz.com>"
LABEL description="AURORA Self-Healing Service for BUTTERFLY Ecosystem"
LABEL version="1.0.0"

# Create non-root user for security
RUN groupadd -r aurora && useradd -r -g aurora aurora

WORKDIR /app

# Copy the built artifact
COPY --from=builder /build/target/aurora-service.jar app.jar

# Create directories for state and logs
RUN mkdir -p /tmp/aurora-streams /var/log/aurora && \
    chown -R aurora:aurora /app /tmp/aurora-streams /var/log/aurora

# Switch to non-root user
USER aurora

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8086/actuator/health || exit 1

# Expose ports
EXPOSE 8086

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=docker"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
