# BUTTERFLY Installation Guide

> Complete guide to installing and configuring the BUTTERFLY ecosystem

**Last Updated**: 2025-12-04  
**Target Audience**: Developers, DevOps engineers, system administrators

---

## Overview

This guide covers multiple installation methods for BUTTERFLY, from quick local development setup to production-ready deployments.

## Prerequisites

### Required Tools

| Tool | Minimum Version | Purpose | Install |
|------|-----------------|---------|---------|
| **Java JDK** | 17 (LTS) | Backend services | [Adoptium](https://adoptium.net/) |
| **Maven** | 3.9+ | Build tool | [Apache Maven](https://maven.apache.org/) |
| **Docker** | 20+ | Container runtime | [Docker](https://docs.docker.com/get-docker/) |
| **Docker Compose** | v2+ | Multi-container orchestration | Included with Docker Desktop |
| **Node.js** | 20+ | Frontend, tooling | [Node.js](https://nodejs.org/) |
| **Git** | 2.30+ | Version control | [Git](https://git-scm.com/) |

### Verify Prerequisites

```bash
# Check Java version
java -version
# Expected: openjdk version "17.x.x"

# Check Maven version
mvn -version
# Expected: Apache Maven 3.9.x

# Check Docker version
docker --version
# Expected: Docker version 20.x.x or higher

# Check Docker Compose version
docker compose version
# Expected: Docker Compose version v2.x.x

# Check Node.js version
node --version
# Expected: v20.x.x or higher

# Check Git version
git --version
# Expected: git version 2.30.x or higher
```

### System Requirements

| Environment | CPU | Memory | Disk |
|-------------|-----|--------|------|
| **Development** | 4 cores | 16 GB RAM | 50 GB |
| **Testing** | 8 cores | 32 GB RAM | 100 GB |
| **Production** | 16+ cores | 64+ GB RAM | 500+ GB SSD |

---

## Installation Methods

### Method 1: Quick Start (Recommended for Development)

The fastest way to get BUTTERFLY running locally:

```bash
# 1. Clone the repository
git clone https://github.com/254CARBON/BUTTERFLY.git
cd BUTTERFLY/apps

# 2. Run the setup script
./scripts/setup.sh

# 3. Start development infrastructure
./scripts/dev-up.sh
```

The setup script will:
1. Verify all prerequisites
2. Install `butterfly-common` to your local Maven repository
3. Configure Husky git hooks for commit validation
4. Install frontend dependencies

### Method 2: Manual Installation

#### Step 1: Clone Repository

```bash
git clone https://github.com/254CARBON/BUTTERFLY.git
cd BUTTERFLY/apps
```

#### Step 2: Install Shared Library

```bash
# Build and install butterfly-common
mvn -f butterfly-common/pom.xml clean install
```

#### Step 3: Build All Services

```bash
# Build all modules (this may take several minutes)
mvn clean install -DskipTests

# Or build specific services
mvn -f PERCEPTION/pom.xml clean install -DskipTests
mvn -f CAPSULE/pom.xml clean install -DskipTests
mvn -f ODYSSEY/pom.xml clean install -DskipTests
mvn -f PLATO/pom.xml clean install -DskipTests
mvn -f butterfly-nexus/pom.xml clean install -DskipTests
```

#### Step 4: Start Infrastructure

```bash
# Start Kafka, PostgreSQL, Redis, etc.
./scripts/dev-up.sh

# Or start specific service infrastructure
docker compose -f CAPSULE/docker-compose.yml up -d
```

#### Step 5: Run Services

```bash
# Run PERCEPTION API
mvn -f PERCEPTION/perception-api/pom.xml spring-boot:run

# Run CAPSULE
mvn -f CAPSULE/pom.xml spring-boot:run

# Run ODYSSEY
mvn -f ODYSSEY/odyssey-core/pom.xml spring-boot:run

# Run PLATO
mvn -f PLATO/pom.xml spring-boot:run
```

### Method 3: Docker Compose (Full Stack)

Run the complete stack in containers:

```bash
# Start all services with Docker Compose
FASTPATH_FULL_STACK=1 ./scripts/dev-up.sh --profile all

# Or start specific service stacks
docker compose -f CAPSULE/docker-compose.yml up -d
docker compose -f PERCEPTION/docker-compose.yml up -d
docker compose -f ODYSSEY/docker-compose.yml up -d
docker compose -f PLATO/docker-compose.yml up -d
```

### Method 4: Kubernetes (Production)

For production deployment, use the Kubernetes manifests:

```bash
# Create namespace
kubectl create namespace butterfly

# Deploy infrastructure (Kafka, databases)
kubectl apply -f kubernetes/infrastructure/ -n butterfly

# Deploy services
kubectl apply -f CAPSULE/k8s/ -n butterfly
kubectl apply -f PERCEPTION/kubernetes/ -n butterfly
kubectl apply -f ODYSSEY/k8s/ -n butterfly
kubectl apply -f PLATO/docker/k8s/ -n butterfly
```

See [Kubernetes Deployment Guide](../operations/deployment/kubernetes.md) for detailed production deployment instructions.

---

## Service Configuration

### Environment Variables

Each service uses environment variables for configuration:

#### Common Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `SCHEMA_REGISTRY_URL` | `http://localhost:8081` | Schema Registry URL |
| `DATABASE_URL` | varies | Database connection URL |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

#### PERCEPTION Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PERCEPTION_API_PORT` | `8080` | API server port |
| `NESSIE_URI` | `http://localhost:19120/api/v1` | Nessie catalog URI |
| `ICEBERG_WAREHOUSE` | `s3://perception-datalake/warehouse` | Iceberg warehouse |

#### CAPSULE Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CAPSULE_API_PORT` | `8081` | API server port |
| `CASSANDRA_HOSTS` | `localhost` | Cassandra contact points |
| `CASSANDRA_PORT` | `9042` | Cassandra CQL port |

#### ODYSSEY Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ODYSSEY_API_PORT` | `8082` | API server port |
| `JANUSGRAPH_HOST` | `localhost` | JanusGraph host |
| `JANUSGRAPH_PORT` | `8182` | JanusGraph port |

#### PLATO Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PLATO_API_PORT` | `8080` | API server port |
| `PLATO_PERSISTENCE_TYPE` | `inmemory` | Persistence backend |
| `JWT_SECRET` | (generated) | JWT signing secret |

### Configuration Files

Each service has configuration in `src/main/resources/`:

```
application.yml           # Default configuration
application-dev.yml       # Development overrides
application-prod.yml      # Production overrides
```

### Profiles

Run services with specific profiles:

```bash
# Development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production profile
java -Dspring.profiles.active=prod -jar service.jar
```

---

## Infrastructure Components

### Kafka Setup

```bash
# Start Kafka with Docker Compose
docker compose -f butterfly-e2e/docker-compose.yml up -d kafka zookeeper

# Create required topics
docker exec -it kafka kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic rim.fast-path \
  --partitions 12 \
  --replication-factor 1

# List topics
docker exec -it kafka kafka-topics.sh --list \
  --bootstrap-server localhost:9092
```

### Database Setup

#### PostgreSQL (PERCEPTION)

```bash
# Start PostgreSQL
docker compose -f PERCEPTION/docker-compose.yml up -d postgres

# Connect to database
docker exec -it perception-postgres psql -U perception -d perception
```

#### Cassandra (CAPSULE, PLATO)

```bash
# Start Cassandra
docker compose -f CAPSULE/docker-compose.yml up -d cassandra

# Wait for Cassandra to be ready (may take 30-60 seconds)
docker exec -it capsule-cassandra cqlsh -e "DESC KEYSPACES"
```

#### JanusGraph (ODYSSEY)

```bash
# Start JanusGraph
docker compose -f ODYSSEY/docker-compose.yml up -d janusgraph

# Verify connection
curl http://localhost:8182/gremlin?gremlin=g.V().count()
```

### Redis Setup

```bash
# Start Redis
docker compose -f PERCEPTION/docker-compose.yml up -d redis

# Verify connection
docker exec -it perception-redis redis-cli ping
# Expected: PONG
```

---

## Verify Installation

### Health Checks

```bash
# Check service health endpoints (default ports)
curl http://localhost:8080/actuator/health  # PERCEPTION (API)
curl http://localhost:8081/actuator/health  # CAPSULE
curl http://localhost:8082/actuator/health  # ODYSSEY
curl http://localhost:8080/actuator/health  # PLATO (standalone; see PLATO/README.md)
curl http://localhost:8084/actuator/health  # NEXUS

# Expected response for healthy service:
# {"status":"UP"}
```

### Run E2E Tests

```bash
# Run golden path integration test
./butterfly-e2e/run-golden-path.sh

# Run full scenario suite
./butterfly-e2e/run-scenarios.sh
```

### Access Swagger UI

| Service | Swagger UI URL |
|---------|----------------|
| PERCEPTION | http://localhost:8080/swagger-ui.html |
| CAPSULE | http://localhost:8081/swagger-ui.html |
| ODYSSEY | http://localhost:8082/swagger-ui.html |
| PLATO | http://localhost:8080/swagger-ui.html |
| NEXUS | http://localhost:8084/swagger-ui.html |

---

## Troubleshooting

### Common Issues

#### Java Version Mismatch

```bash
# Check Java version
java -version

# On macOS, switch Java version
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# On Linux, use update-alternatives
sudo update-alternatives --config java
```

#### Maven Cannot Find butterfly-common

```bash
# Rebuild and install butterfly-common
mvn -f butterfly-common/pom.xml clean install -DskipTests
```

#### Kafka Connection Refused

```bash
# Check if Kafka is running
docker compose -f butterfly-e2e/docker-compose.yml ps

# Check Kafka logs
docker compose -f butterfly-e2e/docker-compose.yml logs kafka

# Restart Kafka
docker compose -f butterfly-e2e/docker-compose.yml restart kafka
```

#### Out of Memory Errors

```bash
# Increase Maven heap size
export MAVEN_OPTS="-Xmx4g -XX:MaxPermSize=512m"

# Increase Docker memory (Docker Desktop settings)
# Recommended: 8GB+ for development
```

#### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change service port via environment variable
PERCEPTION_API_PORT=8090 mvn spring-boot:run
```

### Logs

```bash
# View service logs
docker compose logs -f <service-name>

# View specific service logs
tail -f PERCEPTION/logs/application.log
tail -f PLATO/logs/plato.log
```

---

## Next Steps

| Task | Guide |
|------|-------|
| Make your first API call | [First Steps](first-steps.md) |
| Understand the architecture | [Architecture Overview](architecture-overview.md) |
| Deploy to production | [Kubernetes Deployment](../operations/deployment/kubernetes.md) |
| Configure monitoring | [Monitoring Guide](../operations/monitoring/README.md) |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Quick Start](README.md) | Getting started overview |
| [Development Overview](../../DEVELOPMENT_OVERVIEW.md) | Developer environment setup |
| [Docker Deployment](../operations/deployment/docker.md) | Docker deployment guide |
| [Troubleshooting](../onboarding/troubleshooting.md) | Common issues and solutions |
