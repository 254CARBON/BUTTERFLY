# Creating a New Module

> Step-by-step guide to adding a new Maven module to PERCEPTION or PLATO

**Last Updated**: 2025-12-03  
**Target Audience**: Backend developers

---

## Overview

BUTTERFLY services are organized as multi-module Maven projects. This guide covers creating a new module that follows project conventions.

## When to Create a New Module

Create a new module when:
- Adding a new bounded context or domain capability
- Building a reusable library shared across services
- Separating concerns that have distinct dependencies
- Creating an independently deployable component

**Do NOT create a new module for:**
- Adding a single class or service (add to existing module)
- Minor features that fit existing domains
- Tests (use existing test directories)

---

## Step 1: Choose the Parent Project

| Parent | Use Case | Example Modules |
|--------|----------|-----------------|
| PERCEPTION | Reality mesh, signals, scenarios | perception-signals, perception-scenarios |
| PLATO | Governance, plans, proofs | (single module) |
| butterfly-common | Shared contracts, identities | (library) |

---

## Step 2: Create the Module Directory

```bash
# For PERCEPTION
mkdir -p PERCEPTION/perception-mymodule/src/main/java/com/z254/butterfly/perception/mymodule
mkdir -p PERCEPTION/perception-mymodule/src/main/resources
mkdir -p PERCEPTION/perception-mymodule/src/test/java/com/z254/butterfly/perception/mymodule
mkdir -p PERCEPTION/perception-mymodule/src/test/resources
```

---

## Step 3: Create the Module POM

Create `PERCEPTION/perception-mymodule/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Parent reference -->
    <parent>
        <groupId>com.z254.butterfly</groupId>
        <artifactId>perception</artifactId>
        <version>4.0.0-RC1</version>
    </parent>

    <artifactId>perception-mymodule</artifactId>
    <packaging>jar</packaging>
    
    <name>PERCEPTION - My Module</name>
    <description>Brief description of the module's purpose</description>

    <dependencies>
        <!-- Required: perception-common for shared types -->
        <dependency>
            <groupId>com.z254.butterfly</groupId>
            <artifactId>perception-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- Spring Boot Starter (choose based on needs) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compiler -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            
            <!-- Surefire for unit tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
            
            <!-- JaCoCo for coverage -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Step 4: Register in Parent POM

Add the module to the parent's `<modules>` section:

**For PERCEPTION** (`PERCEPTION/pom.xml`):

```xml
<modules>
    <module>perception-common</module>
    <module>perception-acquisition</module>
    <!-- ... existing modules ... -->
    <module>perception-mymodule</module>  <!-- Add here -->
</modules>
```

**Keep modules in logical order:**
1. Core/common modules first
2. Domain modules alphabetically
3. API/gateway modules last

---

## Step 5: Create Base Classes

### Configuration Class

```java
package com.z254.butterfly.perception.mymodule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

@Configuration
@ComponentScan("com.z254.butterfly.perception.mymodule")
public class MyModuleConfiguration {
    // Bean definitions if needed
}
```

### Service Interface

```java
package com.z254.butterfly.perception.mymodule.service;

public interface MyModuleService {
    /**
     * Describe the method's purpose.
     * @param input the input parameter
     * @return the result
     */
    MyResult process(MyInput input);
}
```

### Service Implementation

```java
package com.z254.butterfly.perception.mymodule.service.impl;

import com.z254.butterfly.perception.mymodule.service.MyModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyModuleServiceImpl implements MyModuleService {

    @Override
    public MyResult process(MyInput input) {
        log.debug("Processing input: {}", input);
        // Implementation
        return new MyResult();
    }
}
```

---

## Step 6: Add Configuration Properties

If your module needs configuration, create a properties class:

```java
package com.z254.butterfly.perception.mymodule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "perception.mymodule")
public class MyModuleProperties {
    
    @NotBlank
    private String name = "default";
    
    @Positive
    private int timeout = 30000;
    
    private boolean enabled = true;
}
```

---

## Step 7: Write Tests

### Unit Test

```java
package com.z254.butterfly.perception.mymodule.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MyModuleServiceImplTest {

    @InjectMocks
    private MyModuleServiceImpl service;

    @Test
    void shouldProcessInput() {
        // Given
        MyInput input = new MyInput("test");
        
        // When
        MyResult result = service.process(input);
        
        // Then
        assertThat(result).isNotNull();
    }
}
```

### Integration Test

```java
package com.z254.butterfly.perception.mymodule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MyModuleIntegrationTest {

    @Autowired
    private MyModuleService myModuleService;

    @Test
    void contextLoads() {
        assertThat(myModuleService).isNotNull();
    }
}
```

---

## Step 8: Create README

Create `PERCEPTION/perception-mymodule/README.md`:

```markdown
# perception-mymodule

Brief description of what this module does.

## Purpose

Explain the business/technical purpose of this module.

## Key Components

| Component | Description |
|-----------|-------------|
| `MyModuleService` | Main service interface |
| `MyModuleConfiguration` | Spring configuration |

## Usage

### As a Dependency

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>perception-mymodule</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Configuration

```yaml
perception:
  mymodule:
    enabled: true
    timeout: 30000
```

## Testing

```bash
mvn -pl perception-mymodule test
```

## Related Documentation

- [Architecture](../docs/ARCHITECTURE.md)
- [Module Design ADR](../docs/adr/XXXX-mymodule-design.md)
```

---

## Step 9: Wire into API (If Needed)

If the module exposes REST endpoints, add it as a dependency to `perception-api`:

```xml
<!-- In perception-api/pom.xml -->
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>perception-mymodule</artifactId>
    <version>${project.version}</version>
</dependency>
```

Then create a controller:

```java
package com.z254.butterfly.perception.api.controller;

import com.z254.butterfly.perception.mymodule.service.MyModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mymodule")
@RequiredArgsConstructor
public class MyModuleController {

    private final MyModuleService myModuleService;

    @GetMapping("/{id}")
    public MyResult get(@PathVariable String id) {
        return myModuleService.process(new MyInput(id));
    }
}
```

---

## Step 10: Build and Verify

```bash
# Build from parent
cd PERCEPTION
mvn clean install -DskipTests

# Run tests for new module
mvn -pl perception-mymodule test

# Run full test suite
mvn verify
```

---

## Checklist

Before submitting your PR:

- [ ] Module follows naming convention: `perception-{name}` or `{service}-{name}`
- [ ] POM inherits from correct parent
- [ ] Module registered in parent POM
- [ ] README created with usage examples
- [ ] Configuration properties documented
- [ ] Unit tests with >80% coverage
- [ ] Integration tests pass
- [ ] No circular dependencies introduced
- [ ] Follows package structure conventions

---

## Common Dependencies by Module Type

### Domain Module
```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>perception-common</artifactId>
</dependency>
```

### Web Module
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Data Module
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

### Kafka Module
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
</dependency>
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Project Structure](project-structure.md) | Codebase organization |
| [Package Guide](package-guide.md) | PLATO package conventions |
| [Common Workflows](common-workflows.md) | Build and test commands |

