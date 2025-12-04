# IDE Setup Guide

This guide covers IDE configuration for BUTTERFLY development, including live templates/snippets, recommended extensions, and project settings.

## VS Code

### Recommended Extensions

| Extension | Purpose |
|-----------|---------|
| Java Extension Pack | Core Java support |
| Spring Boot Extension Pack | Spring Boot tools |
| Lombok Annotations Support | Lombok integration |
| Apache Camel Extension | Camel route highlighting |
| Docker | Container management |
| GitLens | Enhanced Git integration |
| Kubernetes | K8s manifest support |

### Installing BUTTERFLY Snippets

The BUTTERFLY snippets are automatically available when you open the workspace, as they're stored in `.vscode/butterfly.code-snippets`.

Available snippet prefixes:

| Prefix | Description |
|--------|-------------|
| `rimnode` | Parse RimNodeId from string |
| `rimnew` | Create new RimNodeId |
| `tenantctx` | Tenant context try-finally block |
| `kafkalistener` | Kafka listener with DLQ support |
| `kafkasend` | Kafka send with error handling |
| `detector` | Signal detector implementation |
| `detectionctx` | Create DetectionContext |
| `camelroute` | Camel route definition |
| `camelonexception` | Camel onException with DLQ |
| `springrest` | Spring REST controller |
| `springservice` | Spring service implementation |
| `testcontainers` | Testcontainers setup |
| `mocktest` | Mockito test class |
| `apitest` | API integration test |
| `happypath` | Test with happy-path tag |
| `avroschema` | Avro schema definition |
| `lombokbuilder` | Lombok builder class |
| `metriccounter` | Micrometer counter |
| `metrictimer` | Micrometer timer |

### Project Settings

Add to `.vscode/settings.json`:

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "editor.formatOnSave": true,
  "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml"
}
```

---

## IntelliJ IDEA

### Recommended Plugins

| Plugin | Purpose |
|--------|---------|
| Lombok | Lombok annotation processing |
| Apache Camel | Camel route support |
| Kubernetes | K8s integration |
| .env files | Environment file support |
| Avro IDL | Avro schema support |
| Spring Boot Assistant | Spring Boot development |

### Installing BUTTERFLY Live Templates

1. Open **Settings** → **Editor** → **Live Templates**
2. Click the gear icon (⚙️) → **Import**
3. Select `ide-config/intellij/butterfly-live-templates.xml`
4. Click **OK**

Alternatively, copy the file directly:

```bash
# Linux
cp ide-config/intellij/butterfly-live-templates.xml \
   ~/.config/JetBrains/IntelliJIdea<version>/templates/

# macOS
cp ide-config/intellij/butterfly-live-templates.xml \
   ~/Library/Application\ Support/JetBrains/IntelliJIdea<version>/templates/

# Windows
copy ide-config\intellij\butterfly-live-templates.xml ^
   %APPDATA%\JetBrains\IntelliJIdea<version>\templates\
```

### Available Live Templates

| Abbreviation | Description |
|--------------|-------------|
| `rimnode` | Parse RimNodeId |
| `rimnew` | Create new RimNodeId |
| `tenantctx` | Tenant context block |
| `kafkalistener` | Kafka listener with DLQ |
| `kafkasend` | Kafka send with error handling |
| `detector` | Signal detector implementation |
| `detectionctx` | Create DetectionContext |
| `camelroute` | Camel route definition |
| `camelonexception` | Camel onException with DLQ |
| `springrest` | Spring REST controller |
| `springservice` | Spring service implementation |
| `testcontainers` | Testcontainers setup |
| `mocktest` | Mockito test class |
| `apitest` | API integration test |
| `avroschema` | Avro schema definition |

### Code Style

Import the BUTTERFLY code style:

1. Open **Settings** → **Editor** → **Code Style** → **Java**
2. Click gear icon → **Import Scheme** → **IntelliJ IDEA code style XML**
3. Select a Google Java Style configuration

Or configure manually:

- Tab size: 4 spaces
- Continuation indent: 8 spaces
- Line length: 120 characters
- Use wildcard imports after: 999 (effectively disabled)

### Run Configurations

Create run configurations for common tasks:

#### Run PERCEPTION API

```
Main class: com.z254.butterfly.perception.api.PerceptionApiApplication
VM options: -Dspring.profiles.active=local
Working directory: $MODULE_WORKING_DIR$
Use classpath of module: perception-api
```

#### Run Happy Path Tests

```
Test kind: Tags
Tag expression: happy-path
Fork mode: class
Working directory: $MODULE_WORKING_DIR$
```

---

## Common Configuration

### Environment Variables

Create a `.env` file in your project root (ignored by Git):

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/perception
DATABASE_USERNAME=butterfly
DATABASE_PASSWORD=butterfly

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

### Git Configuration

Configure commit signing (recommended for commits):

```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

### Pre-commit Hooks

Husky is configured to run linting on commits. To set up:

```bash
npm install
```

---

## Troubleshooting

### Snippets Not Appearing

**VS Code:**
- Ensure you're in a Java file
- Check that the file is saved (snippets may not appear in untitled files)
- Restart VS Code

**IntelliJ:**
- Check Settings → Editor → Live Templates → BUTTERFLY group is enabled
- Ensure the context (Java code) is appropriate
- Try typing the abbreviation and pressing Tab

### Lombok Not Working

1. Ensure Lombok plugin is installed
2. Enable annotation processing:
   - IntelliJ: Settings → Build → Compiler → Annotation Processors → Enable
   - VS Code: Should be automatic with Java Extension Pack

### Container Connection Issues

If Testcontainers fail to connect:

1. Ensure Docker is running
2. Check Docker socket permissions
3. See `butterfly-testing/README.md` for troubleshooting

---

## Related Documentation

- [Testing Strategy](./testing-strategy.md)
- [butterfly-testing README](../../butterfly-testing/README.md)
- [Development Overview](../../DEVELOPMENT_OVERVIEW.md)
