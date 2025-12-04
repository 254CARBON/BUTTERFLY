# BUTTERFLY Coding Standards

> Code style and conventions for BUTTERFLY development

**Last Updated**: 2025-12-04  
**Target Audience**: Developers

---

## Overview

Consistent code style improves readability and maintainability. These standards apply to all BUTTERFLY services.

---

## Java Code Style

### General

- Java 17 (LTS) baseline
- Follow Google Java Style Guide (with modifications)
- Maximum line length: 120 characters
- Indentation: 4 spaces (no tabs)

### Formatting

Formatting and static analysis are enforced via Maven plugins:

```bash
# From apps/ (root)
mvn -Pquality verify          # Checkstyle + SpotBugs
mvn checkstyle:check          # Style only
```

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `CapsuleService` |
| Interfaces | PascalCase | `CapsuleRepository` |
| Methods | camelCase | `findByScopeId()` |
| Variables | camelCase | `scopeId` |
| Constants | UPPER_SNAKE | `MAX_RETRY_COUNT` |
| Packages | lowercase | `com.z254.butterfly.capsule` |

### Package Structure

```
com.z254.butterfly.<service>/
├── config/           # Configuration classes
├── controller/       # REST controllers
├── service/          # Business logic
├── repository/       # Data access
├── model/            # Domain models
│   ├── entity/       # JPA/Cassandra entities
│   ├── dto/          # Data transfer objects
│   └── event/        # Event objects
├── exception/        # Custom exceptions
└── util/             # Utilities
```

---

## Class Design

### Service Classes

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CapsuleService {
    
    private final CapsuleRepository repository;
    private final EventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public Capsule create(CapsuleRequest request) {
        log.info("Creating capsule for scope: {}", request.getScopeId());
        
        Capsule capsule = mapToEntity(request);
        capsule = repository.save(capsule);
        
        eventPublisher.publish(new CapsuleCreatedEvent(capsule));
        meterRegistry.counter("capsule.created").increment();
        
        return capsule;
    }
}
```

### Controller Classes

```java
@RestController
@RequestMapping("/api/v1/capsules")
@RequiredArgsConstructor
@Validated
@Tag(name = "Capsules", description = "CAPSULE operations")
public class CapsuleController {
    
    private final CapsuleService capsuleService;
    
    @PostMapping
    @Operation(summary = "Create a new capsule")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Capsule created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Capsule exists")
    })
    public ResponseEntity<CapsuleResponse> create(
            @Valid @RequestBody CapsuleRequest request) {
        Capsule capsule = capsuleService.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(CapsuleResponse.from(capsule));
    }
}
```

### Repository Interfaces

```java
@Repository
public interface CapsuleRepository extends CassandraRepository<Capsule, CapsuleId> {
    
    @Query("SELECT * FROM capsules WHERE scope_id = ?0 AND timestamp >= ?1 AND timestamp < ?2")
    List<Capsule> findByScopeAndTimeRange(
        String scopeId, 
        Instant start, 
        Instant end);
    
    @Query("SELECT * FROM capsules WHERE scope_id = ?0 LIMIT 1")
    Optional<Capsule> findLatestByScope(String scopeId);
}
```

---

## Exception Handling

### Custom Exceptions

```java
public class CapsuleNotFoundException extends RuntimeException {
    
    private final String capsuleId;
    
    public CapsuleNotFoundException(String capsuleId) {
        super("Capsule not found: " + capsuleId);
        this.capsuleId = capsuleId;
    }
    
    public String getCapsuleId() {
        return capsuleId;
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(CapsuleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CapsuleNotFoundException ex) {
        log.warn("Capsule not found: {}", ex.getCapsuleId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }
}
```

---

## Documentation

### Javadoc

Required for:
- All public classes
- All public methods
- Complex private methods

```java
/**
 * Creates a new capsule with the given parameters.
 * 
 * <p>The capsule is persisted to the database and an event is published
 * for downstream consumers.
 * 
 * @param request the capsule creation request
 * @return the created capsule
 * @throws CapsuleAlreadyExistsException if a capsule already exists for the same scope and timestamp
 * @throws IllegalArgumentException if the request is invalid
 */
public Capsule create(CapsuleRequest request) {
    // ...
}
```

### Inline Comments

- Use sparingly
- Explain "why", not "what"
- Keep up to date

```java
// Use batch insert for performance (single round-trip to Cassandra)
repository.saveAll(capsules);

// Offset by -1 to handle edge case where timestamp falls exactly on boundary
Instant adjustedStart = start.minusSeconds(1);
```

---

## Testing

### Test Naming

```java
@Test
void create_withValidRequest_returnsCapsule() { }

@Test
void create_withDuplicateScope_throwsException() { }

@Test
void query_withTimeRange_returnsMatchingCapsules() { }
```

### Test Structure (AAA)

```java
@Test
void create_withValidRequest_returnsCapsule() {
    // Arrange
    CapsuleRequest request = CapsuleRequest.builder()
        .scopeId("rim:entity:finance:EURUSD")
        .timestamp(Instant.now())
        .build();
    
    // Act
    Capsule result = capsuleService.create(request);
    
    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getScopeId()).isEqualTo(request.getScopeId());
}
```

### Mock Usage

```java
@ExtendWith(MockitoExtension.class)
class CapsuleServiceTest {
    
    @Mock
    private CapsuleRepository repository;
    
    @InjectMocks
    private CapsuleService service;
    
    @Test
    void create_savesToRepository() {
        // Arrange
        CapsuleRequest request = createRequest();
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        // Act
        service.create(request);
        
        // Assert
        verify(repository).save(argThat(capsule -> 
            capsule.getScopeId().equals(request.getScopeId())));
    }
}
```

---

## Logging

### Log Levels

| Level | Usage |
|-------|-------|
| ERROR | Errors requiring attention |
| WARN | Potential issues, recoverable errors |
| INFO | Important business events |
| DEBUG | Detailed execution flow |
| TRACE | Very detailed debugging |

### Logging Patterns

```java
// Good - use parameterized logging
log.info("Creating capsule for scope: {} at timestamp: {}", scopeId, timestamp);

// Bad - string concatenation
log.info("Creating capsule for scope: " + scopeId);

// Good - include context
log.error("Failed to process capsule: scopeId={}, error={}", scopeId, e.getMessage(), e);

// Bad - missing context
log.error("Failed to process capsule");
```

### Structured Logging

```java
log.info("Capsule created", 
    kv("scopeId", capsule.getScopeId()),
    kv("capsuleId", capsule.getId()),
    kv("duration_ms", duration));
```

---

## Dependency Injection

### Constructor Injection (Preferred)

```java
@Service
@RequiredArgsConstructor
public class CapsuleService {
    private final CapsuleRepository repository;
    private final EventPublisher publisher;
}
```

### Avoid Field Injection

```java
// Bad
@Autowired
private CapsuleRepository repository;

// Good
private final CapsuleRepository repository;

public CapsuleService(CapsuleRepository repository) {
    this.repository = repository;
}
```

---

## Null Safety

### Use Optional

```java
public Optional<Capsule> findById(String id) {
    return repository.findById(id);
}

// Usage
capsuleService.findById(id)
    .orElseThrow(() -> new CapsuleNotFoundException(id));
```

### Annotations

```java
public Capsule create(@NonNull CapsuleRequest request) {
    // request is guaranteed non-null
}

public @Nullable Capsule findLatest(String scopeId) {
    // May return null
}
```

---

## Code Quality Tools

### Checkstyle

```xml
<!-- checkstyle.xml (key rules) -->
<module name="LineLength">
    <property name="max" value="120"/>
</module>
<module name="MissingJavadocMethod">
    <property name="scope" value="public"/>
</module>
<module name="UnusedImports"/>
```

### SpotBugs

```groovy
spotbugs {
    effort = 'max'
    reportLevel = 'high'
}
```

### Enforced via CI

All checks must pass before merge.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Contributing](contributing.md) | Contribution guide |
| [Testing Strategy](testing-strategy.md) | Testing approach |
| [CI/CD](ci-cd.md) | Build pipelines |
