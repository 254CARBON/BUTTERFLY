# Troubleshooting Guide

> Solutions for common issues in BUTTERFLY development

**Last Updated**: 2025-12-03

---

## Build Issues

### `butterfly-common` Not Found

**Error:**
```
Could not resolve dependencies for project: 
Could not find artifact com.z254.butterfly:butterfly-common:jar:0.2.1
```

**Solution:**
```bash
# Install butterfly-common to local Maven repository
mvn -f butterfly-common/pom.xml clean install -DskipTests
```

### Java Version Mismatch

**Error:**
```
Fatal error compiling: invalid target release: 17
```

**Solution:**
```bash
# Check current version
java -version

# On macOS with multiple JDKs
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# On Linux
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Verify
mvn -v  # Should show Java 17
```

### Lombok Not Working

**Symptoms:**
- `@Getter`, `@Setter` not generating methods
- IDE shows errors on generated methods

**Solution:**
1. Install Lombok plugin in IDE
2. Enable annotation processing:
   - IntelliJ: Settings → Build → Compiler → Annotation Processors → Enable
   - VS Code: Install "Lombok Annotations Support" extension

### Maven Out of Memory

**Error:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution:**
```bash
# Set Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"

# Or in ~/.mavenrc
MAVEN_OPTS="-Xmx2g"
```

---

## Runtime Issues

### Port Already in Use

**Error:**
```
Web server failed to start. Port 8080 was already in use.
```

**Solution:**
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port
java -jar app.jar --server.port=8081
```

### Cassandra Connection Refused

**Error:**
```
AllNodesFailedException: All 1 node(s) failed to connect
```

**Solution:**
```bash
# Check if Cassandra is running
docker ps | grep cassandra

# Start Cassandra
docker-compose -f PLATO/docker-compose.yml up -d cassandra

# Wait for startup (can take 60+ seconds)
docker logs -f $(docker ps -q --filter ancestor=cassandra:4.1)

# Verify with cqlsh
docker exec -it $(docker ps -q --filter ancestor=cassandra:4.1) cqlsh
```

### Kafka Connection Refused

**Error:**
```
Connection to node -1 could not be established. Broker may not be available.
```

**Solution:**
```bash
# Start Kafka infrastructure
./scripts/dev-up.sh

# Check Kafka logs
docker-compose -f butterfly-e2e/docker-compose.yml logs kafka

# Verify Kafka is accessible
docker exec -it $(docker ps -q --filter name=kafka) kafka-topics --list --bootstrap-server localhost:9092
```

### WebSocket Connection Failed

**Error:**
```
WebSocket connection to 'ws://localhost:8080/ws/plans/...' failed
```

**Solution:**
1. Check authentication token is valid
2. Verify plan ID exists
3. Check WebSocket endpoint is correct (`/ws/plans/{planId}`)
4. Ensure no proxy is blocking WebSocket upgrade

---

## Test Issues

### Testcontainers Timeout

**Error:**
```
Container startup failed
Timed out waiting for container port to open
```

**Solution:**
```bash
# Increase Docker resources (Docker Desktop)
# Settings → Resources → Memory: 4GB+, CPUs: 2+

# Pull images manually
docker pull cassandra:4.1
docker pull confluentinc/cp-kafka:7.5.0
docker pull postgres:15-alpine

# Increase timeout in test
@Container
static CassandraContainer<?> cassandra = new CassandraContainer<>("cassandra:4.1")
    .withStartupTimeout(Duration.ofMinutes(5));
```

### Tests Pass Locally but Fail in CI

**Common causes:**
1. **Timing issues**: Add `Awaitility` for async assertions
2. **Resource constraints**: CI has limited memory/CPU
3. **Missing dependencies**: Docker images not cached

**Solutions:**
```java
// Use Awaitility for async tests
await().atMost(10, SECONDS)
    .pollInterval(100, MILLISECONDS)
    .until(() -> result.isDone());

// Add explicit waits
@Timeout(30)
void testSlowOperation() { ... }
```

### Flaky Tests

**Symptoms:**
- Tests pass sometimes, fail other times
- Non-deterministic behavior

**Solutions:**
```java
// 1. Use fixed seeds for random data
Random random = new Random(42);

// 2. Mock time-dependent code
@MockBean
Clock clock;

when(clock.instant()).thenReturn(Instant.parse("2025-01-01T00:00:00Z"));

// 3. Use StepVerifier for reactive streams
StepVerifier.create(mono)
    .expectNextCount(1)
    .verifyComplete();
```

### Coverage Below Threshold

**Error:**
```
Rule violated for bundle: instructions covered ratio is 0.65, but expected minimum is 0.70
```

**Solution:**
```bash
# Check coverage report
open target/site/jacoco/index.html

# Identify uncovered code
# Add tests for uncovered branches

# Exclude non-testable code in pom.xml
<excludes>
    <exclude>**/config/**</exclude>
    <exclude>**/dto/**</exclude>
</excludes>
```

---

## Docker Issues

### Permission Denied

**Error:**
```
Got permission denied while trying to connect to the Docker daemon socket
```

**Solution:**
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Log out and back in, or:
newgrp docker

# Verify
docker ps
```

### Docker Compose Version Error

**Error:**
```
services.xxx.depends_on contains an invalid type
```

**Solution:**
```bash
# Check Docker Compose version
docker-compose --version

# Update to v2+
# On Linux:
sudo apt update && sudo apt install docker-compose-plugin

# Use new syntax
docker compose up  # Note: no hyphen
```

### Image Build Fails

**Error:**
```
failed to solve: failed to read dockerfile
```

**Solution:**
```bash
# Ensure correct context
docker build -t app:latest -f ./Dockerfile .
#                          ^^^^context path

# Check Dockerfile location
ls -la Dockerfile
```

---

## IDE Issues

### IntelliJ Cannot Resolve Symbols

**Symptoms:**
- Red underlines on valid code
- "Cannot resolve symbol" errors

**Solution:**
1. File → Invalidate Caches → Invalidate and Restart
2. Right-click `pom.xml` → Maven → Reimport
3. Delete `.idea` folder and reimport project

### VS Code Java Extension Not Working

**Symptoms:**
- No code completion
- Red squiggles everywhere

**Solution:**
```bash
# Clean workspace
rm -rf ~/.vscode/extensions/redhat.java*
rm -rf .vscode/

# Reinstall extensions
code --install-extension vscjava.vscode-java-pack

# Set Java home in settings.json
{
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-17",
      "path": "/path/to/jdk-17",
      "default": true
    }
  ]
}
```

---

## Git Issues

### Commit Rejected by Commitlint

**Error:**
```
⧗   input: bad commit message
✖   subject may not be empty [subject-empty]
✖   type may not be empty [type-empty]
```

**Solution:**
```bash
# Use correct format
git commit -m "feat(scope): description"

# Valid types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert
```

### Husky Hooks Not Running

**Symptoms:**
- Commits go through without validation
- No lint errors shown

**Solution:**
```bash
# Reinstall hooks
npm install
npx husky

# Check hook files exist
ls -la .husky/

# Make hooks executable
chmod +x .husky/*
```

### Merge Conflicts in Generated Files

**Solution:**
```bash
# For lock files, regenerate
rm package-lock.json
npm install

# For pom.xml, resolve manually then:
mvn dependency:resolve
```

---

## Performance Issues

### Slow Maven Builds

**Solution:**
```bash
# Use parallel builds
mvn -T 4 clean install

# Skip unnecessary plugins
mvn clean install -DskipTests -Dcheckstyle.skip -Dspotbugs.skip

# Use build cache (Maven Daemon)
mvnd clean install  # Requires mvnd installation
```

### High Memory Usage

**Symptoms:**
- IDE becomes slow
- `java.lang.OutOfMemoryError`

**Solution:**
```bash
# Increase IDE memory
# IntelliJ: Help → Change Memory Settings → 4096 MB

# Increase Maven memory
export MAVEN_OPTS="-Xmx4g"

# Profile memory usage
jcmd <PID> VM.native_memory summary
```

---

## Getting Help

If you can't resolve an issue:

1. **Search existing issues** on GitHub
2. **Check Slack** `#butterfly-dev` channel
3. **Create a new issue** with:
   - Error message (full stack trace)
   - Steps to reproduce
   - Environment details (OS, Java version, etc.)
   - What you've already tried

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Common Workflows](common-workflows.md) | Build, test, deploy procedures |
| [Project Structure](project-structure.md) | Codebase organization |
| [CI Pipeline Troubleshooting](../../PERCEPTION/docs/runbooks/ci-pipeline-troubleshooting.md) | CI-specific issues |
| [Chaos Recovery](../../PERCEPTION/docs/runbooks/chaos-recovery.md) | Resilience testing issues |

