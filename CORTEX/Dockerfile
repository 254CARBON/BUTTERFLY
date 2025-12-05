# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src
COPY schema ./schema

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Add non-root user
RUN addgroup -g 1001 cortex && \
    adduser -u 1001 -G cortex -s /bin/sh -D cortex

# Copy the built JAR
COPY --from=builder /app/target/cortex-service.jar ./cortex-service.jar

# Set ownership
RUN chown -R cortex:cortex /app

# Switch to non-root user
USER cortex

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

# Expose port
EXPOSE 8086

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar cortex-service.jar"]
