# CI/CD Troubleshooting Guide

> Diagnose and resolve common CI/CD failures

**Last Updated**: 2025-12-03  
**Target Audience**: All contributors

---

## Quick Diagnosis

### Step 1: Identify the Failing Job

1. Go to GitHub Actions → Select failed workflow run
2. Expand the failed job (red X)
3. Find the failing step

### Step 2: Check Common Causes

| Symptom | Likely Cause | Solution |
|---------|--------------|----------|
| `butterfly-common` not found | Dependency not installed | See [Missing Dependency](#missing-butterfly-common-dependency) |
| Test timeout | Slow service startup | See [Testcontainers Issues](#testcontainers-issues) |
| Checkstyle violation | Code style issue | See [Code Quality Failures](#code-quality-failures) |
| Security finding | Vulnerable dependency | See [Security Scan Failures](#security-scan-failures) |
| Playwright failure | UI test flakiness | See [Portal E2E Failures](#portal-e2e-failures) |
| Docker build failure | Build context issue | See [Docker Build Issues](#docker-build-issues) |

---

## Common Failures

### Missing butterfly-common Dependency

**Error:**
```
[ERROR] Failed to execute goal on project perception-api: 
Could not resolve dependencies for project com.z254.butterfly:perception-api:jar:4.0.0: 
Could not find artifact com.z254.butterfly:butterfly-common:jar:0.2.1
```

**Cause:** The `butterfly-common` library must be installed before building dependent modules.

**Solution:**

1. **In CI**: Verify the workflow has the install step:
   ```yaml
   - name: Install butterfly-common
     run: mvn -B -f butterfly-common/pom.xml install -DskipTests
   ```

2. **Locally**:
   ```bash
   mvn -f butterfly-common/pom.xml install -DskipTests
   ```

---

### Testcontainers Issues

**Error:**
```
org.testcontainers.containers.ContainerLaunchException: 
Container startup failed
```

**Causes:**
- Docker daemon not running
- Insufficient container resources
- Port conflicts

**Solutions:**

1. **Check Docker resources** (CI runners have limited memory):
   ```yaml
   services:
     postgres:
       image: postgres:15
       options: >-
         --health-cmd pg_isready
         --health-interval 10s
         --health-timeout 5s
         --health-retries 5
   ```

2. **Increase wait times** for slow services:
   ```java
   @Container
   static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
       .withStartupTimeout(Duration.ofMinutes(2));
   ```

3. **Locally**, ensure Docker has adequate resources:
   - Memory: 8GB+
   - CPUs: 4+

---

### Code Quality Failures

#### Checkstyle Violations

**Error:**
```
[ERROR] src/main/java/com/z254/.../MyClass.java:[15,5] 
(indentation) Indentation: 'method def modifier' has incorrect indentation level 4, expected level should be 2.
```

**Solution:**

1. **Check Google Style Guide**: BUTTERFLY uses Google Java Style
2. **Auto-format in IDE**:
   - IntelliJ: Code → Reformat Code (Ctrl+Alt+L)
   - VS Code: Format Document (Shift+Alt+F)
3. **Run locally before push**:
   ```bash
   mvn checkstyle:check
   ```

#### SpotBugs Findings

**Error:**
```
[ERROR] Medium: Null pointer dereference in com.z254.butterfly.plato.MyClass.myMethod()
```

**Solution:**

1. **Review the finding**: SpotBugs reports are in `target/spotbugsXml.xml`
2. **Fix the issue**: Add null checks, use Optional, etc.
3. **If false positive**, add to exclusion file:
   ```xml
   <!-- spotbugs-exclude.xml -->
   <Match>
     <Class name="com.z254.butterfly.plato.MyClass"/>
     <Method name="myMethod"/>
     <Bug pattern="NP_NULL_ON_SOME_PATH"/>
   </Match>
   ```

---

### Security Scan Failures

#### OWASP Dependency Check

**Error:**
```
[ERROR] Dependency-Check Failure:
One or more dependencies have vulnerabilities with CVSS >= 7
```

**Solution:**

1. **Review the report**: Download `dependency-check-report.html` artifact
2. **Upgrade vulnerable dependency**:
   ```xml
   <dependency>
     <groupId>org.example</groupId>
     <artifactId>vulnerable-lib</artifactId>
     <version>FIXED_VERSION</version>
   </dependency>
   ```
3. **If no fix available**, add suppression:
   ```xml
   <!-- .github/dependency-check-suppressions.xml -->
   <suppress>
     <notes>False positive or mitigated</notes>
     <cve>CVE-2024-XXXXX</cve>
   </suppress>
   ```

#### Secret Scanning

**Error:**
```
TruffleHog found verified secret in commit abc123
```

**Solution:**

1. **DO NOT push more commits** - the secret is exposed
2. **Rotate the secret immediately** in the relevant system
3. **Remove from history** (if possible):
   ```bash
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch path/to/secret" \
     --prune-empty --tag-name-filter cat -- --all
   git push --force
   ```
4. **Add to .gitignore** to prevent future exposure

---

### Portal E2E Failures

#### Playwright Test Timeout

**Error:**
```
Error: locator.click: Timeout 30000ms exceeded.
waiting for locator('button[data-testid="submit"]')
```

**Solutions:**

1. **Download trace artifact**:
   - Go to workflow run → Artifacts → `playwright-report`
   - Open `trace.zip` in [trace.playwright.dev](https://trace.playwright.dev)

2. **Increase timeout** for slow operations:
   ```typescript
   await page.locator('button[data-testid="submit"]').click({ timeout: 60000 });
   ```

3. **Wait for network idle**:
   ```typescript
   await page.goto('/signals/clusters', { waitUntil: 'networkidle' });
   ```

4. **Run locally with UI**:
   ```bash
   cd PERCEPTION/perception-portal
   npm run test:e2e:ui
   ```

#### Flaky Tests

**Symptom:** Test passes locally but fails intermittently in CI

**Solutions:**

1. **Add retry logic**:
   ```typescript
   // playwright.config.ts
   export default defineConfig({
     retries: process.env.CI ? 2 : 0,
   });
   ```

2. **Wait for specific conditions**:
   ```typescript
   await expect(page.locator('.cluster-list')).toBeVisible();
   await expect(page.locator('.loading')).toBeHidden();
   ```

3. **Avoid time-based waits**:
   ```typescript
   // Bad
   await page.waitForTimeout(5000);
   
   // Good
   await page.waitForSelector('.data-loaded');
   ```

---

### Docker Build Issues

#### Build Context Too Large

**Error:**
```
error: failed to solve: error from sender: context size limit exceeded
```

**Solution:**

1. **Check .dockerignore**:
   ```
   # .dockerignore
   target/
   node_modules/
   .git/
   *.log
   ```

2. **Use multi-stage builds**:
   ```dockerfile
   FROM maven:3.9-eclipse-temurin-17 AS builder
   COPY pom.xml .
   RUN mvn dependency:go-offline
   COPY src ./src
   RUN mvn package -DskipTests
   
   FROM eclipse-temurin:17-jre
   COPY --from=builder /target/*.jar app.jar
   ```

#### Image Security Scan Failure

**Error:**
```
Trivy found 3 CRITICAL vulnerabilities
```

**Solution:**

1. **Update base image**:
   ```dockerfile
   # Use latest patched version
   FROM eclipse-temurin:17.0.9_9-jre-alpine
   ```

2. **Run security updates in Dockerfile**:
   ```dockerfile
   RUN apk update && apk upgrade --no-cache
   ```

---

### Maven Build Failures

#### Out of Memory

**Error:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution:**

1. **Set Maven opts in workflow**:
   ```yaml
   env:
     MAVEN_OPTS: "-Xmx4g -XX:MaxMetaspaceSize=512m"
   ```

2. **Use parallel builds wisely**:
   ```bash
   # Reduce parallelism if OOM
   mvn clean install -T 1C  # 1 thread per core
   ```

#### Dependency Resolution Failure

**Error:**
```
Could not resolve dependencies: Failed to collect dependencies
```

**Solutions:**

1. **Clear Maven cache**:
   ```yaml
   - name: Clear Maven cache
     run: rm -rf ~/.m2/repository/com/z254
   ```

2. **Force update**:
   ```bash
   mvn clean install -U
   ```

---

## CI Performance Optimization

### Caching Best Practices

```yaml
# Maven dependencies
- uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-

# Node modules
- uses: actions/cache@v4
  with:
    path: ~/.npm
    key: ${{ runner.os }}-node-${{ hashFiles('**/package-lock.json') }}
```

### Parallel Job Execution

Structure jobs to run in parallel where possible:

```yaml
jobs:
  build:
    # Runs first
  
  unit-tests:
    needs: build
    # Runs after build
  
  integration-tests:
    needs: build
    # Runs parallel with unit-tests
  
  coverage:
    needs: build
    # Runs parallel with tests
```

### Skip Unnecessary Work

Use path filters to avoid running irrelevant jobs:

```yaml
on:
  push:
    paths:
      - 'PLATO/**'
      - '!PLATO/docs/**'  # Skip doc-only changes
```

---

## Getting Help

If you're stuck:

1. **Search existing issues**: Check if someone had the same problem
2. **Check workflow history**: Compare with recent successful runs
3. **Ask in Slack**: `#butterfly-dev` channel
4. **Create an issue**: Include workflow link and error logs

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [CI/CD Overview](README.md) | Pipeline documentation |
| [Branch Protection](branch-protection.md) | Branch rules |
| [Portal E2E Debugging](../../../PERCEPTION/docs/runbooks/portal-e2e-debugging.md) | Detailed Playwright debugging |
| [Common Issues](../runbooks/common-issues.md) | General troubleshooting |

