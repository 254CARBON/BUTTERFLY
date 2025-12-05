# Contributing to AURORA

Thank you for your interest in contributing to AURORA!

## Development Setup

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- Kafka + Schema Registry
- Cassandra
- Redis

### Local Development

```bash
# Start infrastructure
docker-compose up -d kafka schema-registry redis cassandra

# Build
mvn clean install

# Run tests
mvn test

# Run the service
mvn spring-boot:run
```

## Code Standards

### Style Guidelines

- Follow Google Java Style Guide
- Use Lombok for boilerplate reduction
- Prefer reactive programming patterns (Mono/Flux)
- Write comprehensive Javadoc for public APIs

### Commit Messages

Use conventional commit format:
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `refactor:` - Code refactoring
- `test:` - Test additions/changes
- `chore:` - Build/tooling changes

### Testing

- Unit tests for all business logic
- Integration tests for Kafka Streams
- E2E tests for complete flows
- Aim for 80%+ code coverage

## Architecture Guidelines

### Component Design

1. **RCA Engine** - Keep analysis logic stateless
2. **Remediation** - Always include rollback capability
3. **Immunity** - Design for eventual consistency
4. **APIs** - Follow REST best practices

### Error Handling

- Use circuit breakers for external calls
- Implement fallback chains for critical paths
- Log all errors with correlation IDs

## Pull Request Process

1. Create feature branch from `main`
2. Write tests for new functionality
3. Update documentation as needed
4. Submit PR with detailed description
5. Address review comments
6. Squash and merge

## Questions?

Contact the BUTTERFLY team at butterfly@254studioz.com
