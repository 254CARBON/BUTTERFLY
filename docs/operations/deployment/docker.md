# Docker Deployment

> Deploy BUTTERFLY using Docker and Docker Compose

**Last Updated**: 2025-12-03  
**Target Audience**: Developers, DevOps engineers

---

## Prerequisites

- Docker Engine 24.0+
- Docker Compose 2.20+
- 16 GB RAM minimum (32 GB recommended)
- 50 GB disk space

---

## Quick Start

### Clone and Configure

```bash
# Clone repository
git clone https://github.com/your-org/butterfly.git
cd butterfly/apps

# Copy environment template
cp .env.example .env

# Edit configuration
nano .env
```

### Start Services

```bash
# Start all services
docker compose up -d

# Watch logs
docker compose logs -f

# Check status
docker compose ps
```

---

## Docker Compose Configuration

### Full Stack (`docker-compose.yml`)

```yaml
version: '3.8'

services:
  # ==========================================================================
  # Infrastructure Services
  # ==========================================================================
  
  cassandra:
    image: cassandra:4.1
    container_name: butterfly-cassandra
    ports:
      - "9042:9042"
    environment:
      - CASSANDRA_CLUSTER_NAME=butterfly
      - CASSANDRA_DC=datacenter1
      - CASSANDRA_RACK=rack1
    volumes:
      - cassandra-data:/var/lib/cassandra
    healthcheck:
      test: ["CMD", "cqlsh", "-e", "describe keyspaces"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  postgres:
    image: postgres:15
    container_name: butterfly-postgres
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=perception
      - POSTGRES_USER=butterfly
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-butterfly}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U butterfly -d perception"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - butterfly-net

  redis:
    image: redis:7
    container_name: butterfly-redis
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - butterfly-net

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: butterfly-zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
    networks:
      - butterfly-net

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: butterfly-kafka
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka-data:/var/lib/kafka/data
    depends_on:
      - zookeeper
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  schema-registry:
    image: confluentinc/cp-schema-registry:7.5.0
    container_name: butterfly-schema-registry
    ports:
      - "8081:8081"
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      - kafka
    networks:
      - butterfly-net

  janusgraph:
    image: janusgraph/janusgraph:1.0.0
    container_name: butterfly-janusgraph
    ports:
      - "8182:8182"
    environment:
      JAVA_OPTIONS: "-Xms512m -Xmx2g"
    volumes:
      - janusgraph-data:/var/lib/janusgraph
    networks:
      - butterfly-net

  clickhouse:
    image: clickhouse/clickhouse-server:23.8
    container_name: butterfly-clickhouse
    ports:
      - "8123:8123"
      - "9000:9000"
    volumes:
      - clickhouse-data:/var/lib/clickhouse
    networks:
      - butterfly-net

  # ==========================================================================
  # BUTTERFLY Services
  # ==========================================================================

  capsule:
    build:
      context: ./capsule
      dockerfile: Dockerfile
    container_name: butterfly-capsule
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - CASSANDRA_CONTACT_POINTS=cassandra:9042
      - REDIS_HOST=redis
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - SCHEMA_REGISTRY_URL=http://schema-registry:8081
    depends_on:
      cassandra:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  odyssey:
    build:
      context: ./odyssey
      dockerfile: Dockerfile
    container_name: butterfly-odyssey
    ports:
      - "8084:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JANUSGRAPH_HOST=janusgraph
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - SCHEMA_REGISTRY_URL=http://schema-registry:8081
    depends_on:
      janusgraph:
        condition: service_started
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  perception:
    build:
      context: ./perception
      dockerfile: Dockerfile
    container_name: butterfly-perception
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - POSTGRES_HOST=postgres
      - CLICKHOUSE_HOST=clickhouse
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - SCHEMA_REGISTRY_URL=http://schema-registry:8081
    depends_on:
      postgres:
        condition: service_healthy
      clickhouse:
        condition: service_started
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  plato:
    build:
      context: ./plato
      dockerfile: Dockerfile
    container_name: butterfly-plato
    ports:
      - "8086:8086"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - CASSANDRA_CONTACT_POINTS=cassandra:9042
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - SCHEMA_REGISTRY_URL=http://schema-registry:8081
    depends_on:
      cassandra:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8086/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

  nexus:
    build:
      context: ./butterfly-nexus
      dockerfile: Dockerfile
    container_name: butterfly-nexus
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - CAPSULE_URL=http://capsule:8080
      - ODYSSEY_URL=http://odyssey:8081
      - PERCEPTION_URL=http://perception:8082
      - PLATO_URL=http://plato:8086
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - REDIS_HOST=redis
    depends_on:
      capsule:
        condition: service_healthy
      odyssey:
        condition: service_healthy
      perception:
        condition: service_healthy
      plato:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - butterfly-net

networks:
  butterfly-net:
    driver: bridge

volumes:
  cassandra-data:
  postgres-data:
  redis-data:
  zookeeper-data:
  kafka-data:
  janusgraph-data:
  clickhouse-data:
```

---

## Service-Specific Deployment

### Individual Service

```bash
# Build and start specific service
docker compose build capsule
docker compose up -d capsule

# View service logs
docker compose logs -f capsule
```

### Infrastructure Only

```bash
# Start only infrastructure
docker compose up -d cassandra postgres redis kafka zookeeper schema-registry janusgraph clickhouse
```

---

## Development Mode

### Hot Reload Configuration

```yaml
# docker-compose.override.yml
version: '3.8'

services:
  capsule:
    volumes:
      - ./capsule/src:/app/src
      - ./capsule/build:/app/build
    environment:
      - SPRING_DEVTOOLS_RESTART_ENABLED=true
```

### Debug Mode

```bash
# Start with debug ports
JAVA_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  docker compose up capsule
```

---

## Useful Commands

```bash
# Stop all services
docker compose down

# Stop and remove volumes
docker compose down -v

# Rebuild all images
docker compose build --no-cache

# Scale a service
docker compose up -d --scale nexus=3

# View resource usage
docker stats

# Execute command in container
docker compose exec capsule sh

# View container logs
docker compose logs -f --tail=100 capsule
```

---

## Troubleshooting

### Service Won't Start

```bash
# Check logs
docker compose logs capsule

# Check container status
docker compose ps

# Verify dependencies
docker compose exec capsule curl -f http://cassandra:9042
```

### Memory Issues

```bash
# Increase Docker memory limit
# Docker Desktop: Settings → Resources → Memory

# Or limit service memory
# In docker-compose.yml:
services:
  capsule:
    deploy:
      resources:
        limits:
          memory: 2G
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Kubernetes Deployment](kubernetes.md) | K8s deployment |
| [Production Checklist](production-checklist.md) | Go-live checklist |

