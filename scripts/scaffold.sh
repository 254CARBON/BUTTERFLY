#!/usr/bin/env bash
# =============================================================================
# BUTTERFLY Scaffolding CLI
# 
# A developer experience tool for generating boilerplate code and project
# structures following BUTTERFLY ecosystem conventions.
#
# Usage: ./scripts/scaffold.sh <command> [options]
#
# Commands:
#   service         Create a new microservice skeleton
#   controller      Add a REST controller to an existing service
#   entity          Add a JPA entity with repository
#   kafka-handler   Add a Kafka consumer/producer handler
#   migration       Add a Flyway migration
#   test            Add test scaffolding (unit, integration, chaos)
#   connector       [PERCEPTION] Add acquisition connector (route, processors, config)
#   signal-detector [PERCEPTION] Add signal detector implementation
#   reasoning-rule  [PERCEPTION] Add reasoning rule implementation
#   scenario-type   [PERCEPTION] Add scenario type handler
#   scenario        [PERCEPTION] Add scenario generation school
#
# Examples:
#   ./scripts/scaffold.sh service --name market-data --port 8090
#   ./scripts/scaffold.sh controller --service PERCEPTION --name SignalController
#   ./scripts/scaffold.sh entity --service ODYSSEY --name Actor --table actors
#   ./scripts/scaffold.sh connector --name WebScraper --type WEB_CRAWL
#   ./scripts/scaffold.sh signal-detector --name Anomaly
#   ./scripts/scaffold.sh reasoning-rule --name TemporalConstraint
#   ./scripts/scaffold.sh scenario-type --name ContingencyPlan
#   ./scripts/scaffold.sh scenario --name EmergingThreat --school PATTERN_MATCHING
#
# Related Epic: .github/dx-issues/epic-scaffolding-cli.md
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEMPLATES_DIR="${PROJECT_ROOT}/scripts/templates"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values
JAVA_VERSION="21"
SPRING_BOOT_VERSION="3.2.1"
GROUP_ID="com.z254.butterfly"

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

print_banner() {
    echo -e "${CYAN}"
    cat << 'EOF'
    ____  __  _____________ ____  ______  __
   / __ )/ / / /_  __/_  __/ __ \/ ____/ / /  __  __
  / __  / / / / / /   / / / /_/ / /_    / /  / / / /
 / /_/ / /_/ / / /   / / / _, _/ __/   / /__/ /_/ /
/_____/\____/ /_/   /_/ /_/ |_/_/     /_____|__, /
                                           /____/
       Scaffolding CLI v1.0.0
EOF
    echo -e "${NC}"
}

show_help() {
    print_banner
    cat << 'EOF'
USAGE:
    scaffold.sh <command> [options]

COMMANDS:
    service         Create a new microservice skeleton
    controller      Add a REST controller to a service
    entity          Add a JPA entity with repository
    kafka-handler   Add Kafka consumer/producer
    migration       Add a Flyway migration file
    test            Add test scaffolding
    docs            Generate documentation from templates
    connector       [PERCEPTION] Add acquisition connector
    signal-detector [PERCEPTION] Add signal detector
    reasoning-rule  [PERCEPTION] Add reasoning rule
    scenario-type   [PERCEPTION] Add scenario type handler
    scenario        [PERCEPTION] Add scenario generation school
    help            Show this help message

OPTIONS:
    -n, --name      Name of the component (required for most commands)
    -s, --service   Target service (e.g., PERCEPTION, ODYSSEY)
    -p, --port      Port number (for service creation)
    -t, --table     Database table name (for entity)
    -o, --output    Output path for generated documentation
    --title         Title for documentation (ADR, runbook, api-guide)
    --author        Author name(s) for ADR
    --dry-run       Show what would be created without making changes

EXAMPLES:
    Create a new service:
        ./scripts/scaffold.sh service --name market-data --port 8090

    Add a controller to PERCEPTION:
        ./scripts/scaffold.sh controller --service PERCEPTION --name Signal

    Add an entity to ODYSSEY:
        ./scripts/scaffold.sh entity --service ODYSSEY --name Actor --table actors

    Add Kafka handler:
        ./scripts/scaffold.sh kafka-handler --service PERCEPTION --name SignalEvent

    Create test scaffolding:
        ./scripts/scaffold.sh test --service ODYSSEY --type integration

    Generate documentation:
        ./scripts/scaffold.sh docs readme --name MyModule --service PERCEPTION
        ./scripts/scaffold.sh docs runbook --title "Service Recovery" --service PERCEPTION
        ./scripts/scaffold.sh docs adr --title "Use Event Sourcing" --author "Jane Doe"
        ./scripts/scaffold.sh docs api-guide --name "Acquisition" --service PERCEPTION

For more information, see: docs/development/scaffolding.md
EOF
}

validate_service() {
    local service="$1"
    local valid_services=("CAPSULE" "ODYSSEY" "PERCEPTION" "PLATO" "SYNAPSE" "butterfly-nexus")
    
    for valid in "${valid_services[@]}"; do
        if [[ "$service" == "$valid" ]]; then
            return 0
        fi
    done
    
    log_error "Invalid service: $service"
    log_info "Valid services: ${valid_services[*]}"
    exit 1
}

to_pascal_case() {
    echo "$1" | sed -r 's/(^|-)([a-z])/\U\2/g'
}

to_camel_case() {
    local pascal=$(to_pascal_case "$1")
    echo "${pascal,}"
}

to_snake_case() {
    echo "$1" | sed -r 's/([A-Z])/_\L\1/g' | sed 's/^_//'
}

ensure_templates_dir() {
    if [[ ! -d "$TEMPLATES_DIR" ]]; then
        log_info "Creating templates directory..."
        mkdir -p "$TEMPLATES_DIR"
        create_default_templates
    fi
}

create_default_templates() {
    # Controller template
    cat > "${TEMPLATES_DIR}/controller.java.tmpl" << 'TMPL'
package {{PACKAGE}}.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for {{NAME}} operations.
 *
 * @see <a href="https://docs.butterfly.example.com/api/{{SERVICE_LOWER}}">API Documentation</a>
 */
@RestController
@RequestMapping("/api/v1/{{ENDPOINT}}")
@RequiredArgsConstructor
@Slf4j
public class {{NAME}}Controller {

    // TODO: Inject service
    // private final {{NAME}}Service service;

    @GetMapping
    public ResponseEntity<List<Object>> list() {
        log.info("Listing {{NAME_LOWER}}s");
        // TODO: Implement
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getById(@PathVariable String id) {
        log.info("Getting {{NAME_LOWER}} by id: {}", id);
        // TODO: Implement
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody Object request) {
        log.info("Creating {{NAME_LOWER}}");
        // TODO: Implement
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id, @Valid @RequestBody Object request) {
        log.info("Updating {{NAME_LOWER}}: {}", id);
        // TODO: Implement
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.info("Deleting {{NAME_LOWER}}: {}", id);
        // TODO: Implement
        return ResponseEntity.noContent().build();
    }
}
TMPL

    # Entity template
    cat > "${TEMPLATES_DIR}/entity.java.tmpl" << 'TMPL'
package {{PACKAGE}}.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * {{NAME}} entity.
 */
@Entity
@Table(name = "{{TABLE}}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class {{NAME}} {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
TMPL

    # Repository template
    cat > "${TEMPLATES_DIR}/repository.java.tmpl" << 'TMPL'
package {{PACKAGE}}.repository;

import {{PACKAGE}}.domain.{{NAME}};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {{NAME}} entities.
 */
@Repository
public interface {{NAME}}Repository extends JpaRepository<{{NAME}}, UUID> {

    Optional<{{NAME}}> findByName(String name);

    @Query("SELECT e FROM {{NAME}} e WHERE e.name LIKE %:search%")
    List<{{NAME}}> search(String search);
}
TMPL

    # Kafka handler template
    cat > "${TEMPLATES_DIR}/kafka-handler.java.tmpl" << 'TMPL'
package {{PACKAGE}}.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka handler for {{NAME}} events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class {{NAME}}KafkaHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_IN = "{{TOPIC_IN}}";
    private static final String TOPIC_OUT = "{{TOPIC_OUT}}";

    @KafkaListener(
        topics = TOPIC_IN,
        groupId = "{{SERVICE_LOWER}}-{{NAME_LOWER}}-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle{{NAME}}Event(Object event) {
        log.info("Received {{NAME}} event: {}", event);
        try {
            // TODO: Process event
            
            // Optionally publish result
            // kafkaTemplate.send(TOPIC_OUT, result);
        } catch (Exception e) {
            log.error("Failed to process {{NAME}} event", e);
            throw e; // Let error handler deal with it
        }
    }

    public void publish{{NAME}}Event(Object event) {
        log.info("Publishing {{NAME}} event to {}", TOPIC_OUT);
        kafkaTemplate.send(TOPIC_OUT, event);
    }
}
TMPL

    # Integration test template
    cat > "${TEMPLATES_DIR}/integration-test.java.tmpl" << 'TMPL'
package {{PACKAGE}};

import com.z254.butterfly.testing.base.AbstractApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {{NAME}}.
 */
class {{NAME}}IntegrationTest extends AbstractApiIntegrationTest {

    @Nested
    @DisplayName("GET /api/v1/{{ENDPOINT}}")
    class ListTests {

        @Test
        @DisplayName("should return empty list when no items exist")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/{{ENDPOINT}}")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/{{ENDPOINT}}")
    class CreateTests {

        @Test
        @DisplayName("should create new item")
        void shouldCreateItem() throws Exception {
            String json = """
                {
                    "name": "test"
                }
                """;

            mockMvc.perform(post("/api/v1/{{ENDPOINT}}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isOk());
        }
    }
}
TMPL

    log_success "Created default templates in ${TEMPLATES_DIR}"
}

# -----------------------------------------------------------------------------
# Command: service
# -----------------------------------------------------------------------------

cmd_service() {
    local name=""
    local port=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -p|--port) port="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Service name is required (--name)"
        exit 1
    fi
    
    if [[ -z "$port" ]]; then
        log_error "Port is required (--port)"
        exit 1
    fi
    
    local service_dir="${PROJECT_ROOT}/${name}"
    local package_path="${GROUP_ID//.//}/${name//-/}"
    
    log_info "Creating service: $name on port $port"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - ${service_dir}/pom.xml"
        echo "  - ${service_dir}/src/main/java/.../${name}Application.java"
        echo "  - ${service_dir}/src/main/resources/application.yml"
        echo "  - ${service_dir}/src/test/java/.../ApplicationTests.java"
        return
    fi
    
    # Create directory structure
    mkdir -p "${service_dir}/src/main/java/${package_path}"
    mkdir -p "${service_dir}/src/main/resources"
    mkdir -p "${service_dir}/src/test/java/${package_path}"
    
    local pascal_name=$(to_pascal_case "$name")
    
    # Create pom.xml
    cat > "${service_dir}/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>${GROUP_ID}</groupId>
        <artifactId>butterfly-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../butterfly-parent/pom.xml</relativePath>
    </parent>

    <artifactId>${name}</artifactId>
    <name>BUTTERFLY ${pascal_name}</name>
    <description>${pascal_name} service for BUTTERFLY ecosystem</description>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- BUTTERFLY Common -->
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>butterfly-common</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>butterfly-security-starter</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>butterfly-testing</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

    # Create main application class
    cat > "${service_dir}/src/main/java/${package_path}/${pascal_name}Application.java" << EOF
package ${GROUP_ID}.${name//-/};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ${pascal_name}Application {

    public static void main(String[] args) {
        SpringApplication.run(${pascal_name}Application.class, args);
    }
}
EOF

    # Create application.yml
    cat > "${service_dir}/src/main/resources/application.yml" << EOF
spring:
  application:
    name: ${name}

server:
  port: ${port}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
EOF

    # Create test class
    cat > "${service_dir}/src/test/java/${package_path}/${pascal_name}ApplicationTests.java" << EOF
package ${GROUP_ID}.${name//-/};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ${pascal_name}ApplicationTests {

    @Test
    void contextLoads() {
    }
}
EOF

    log_success "Created service: $name"
    log_info "Next steps:"
    echo "  1. Add module to root pom.xml"
    echo "  2. Update Grafana dashboard config"
    echo "  3. Add Kubernetes manifests"
}

# -----------------------------------------------------------------------------
# Command: controller
# -----------------------------------------------------------------------------

cmd_controller() {
    local name=""
    local service=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Controller name is required (--name)"
        exit 1
    fi
    
    if [[ -z "$service" ]]; then
        log_error "Service is required (--service)"
        exit 1
    fi
    
    validate_service "$service"
    ensure_templates_dir
    
    local service_lower=$(echo "$service" | tr '[:upper:]' '[:lower:]')
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]')
    local endpoint=$(to_snake_case "$name" | tr '_' '-')
    
    # Determine package structure based on service
    local package=""
    local src_path=""
    
    case "$service" in
        PERCEPTION)
            package="${GROUP_ID}.perception.api"
            src_path="${PROJECT_ROOT}/PERCEPTION/perception-api/src/main/java/${GROUP_ID//.//}/perception/api"
            ;;
        ODYSSEY)
            package="${GROUP_ID}.odyssey.api"
            src_path="${PROJECT_ROOT}/ODYSSEY/odyssey-core/src/main/java/${GROUP_ID//.//}/odyssey/api"
            ;;
        *)
            package="${GROUP_ID}.${service_lower}.api"
            src_path="${PROJECT_ROOT}/${service}/src/main/java/${GROUP_ID//.//}/${service_lower}/api"
            ;;
    esac
    
    local output_file="${src_path}/${pascal_name}Controller.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $output_file"
        return
    fi
    
    mkdir -p "$src_path"
    
    # Use template and replace placeholders
    sed -e "s/{{PACKAGE}}/${package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{ENDPOINT}}/${endpoint}/g" \
        -e "s/{{SERVICE_LOWER}}/${service_lower}/g" \
        "${TEMPLATES_DIR}/controller.java.tmpl" > "$output_file"
    
    log_success "Created controller: $output_file"
}

# -----------------------------------------------------------------------------
# Command: entity
# -----------------------------------------------------------------------------

cmd_entity() {
    local name=""
    local service=""
    local table=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            -t|--table) table="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Entity name is required (--name)"
        exit 1
    fi
    
    if [[ -z "$service" ]]; then
        log_error "Service is required (--service)"
        exit 1
    fi
    
    if [[ -z "$table" ]]; then
        table=$(to_snake_case "$name")s
        log_info "Using default table name: $table"
    fi
    
    validate_service "$service"
    ensure_templates_dir
    
    local service_lower=$(echo "$service" | tr '[:upper:]' '[:lower:]')
    local pascal_name=$(to_pascal_case "$name")
    
    # Determine package structure
    local base_package="${GROUP_ID}.${service_lower}"
    local base_path=""
    
    case "$service" in
        PERCEPTION)
            base_package="${GROUP_ID}.perception"
            base_path="${PROJECT_ROOT}/PERCEPTION/perception-api/src/main/java/${GROUP_ID//.//}/perception"
            ;;
        ODYSSEY)
            base_package="${GROUP_ID}.odyssey"
            base_path="${PROJECT_ROOT}/ODYSSEY/odyssey-core/src/main/java/${GROUP_ID//.//}/odyssey"
            ;;
        *)
            base_path="${PROJECT_ROOT}/${service}/src/main/java/${GROUP_ID//.//}/${service_lower}"
            ;;
    esac
    
    local entity_file="${base_path}/domain/${pascal_name}.java"
    local repo_file="${base_path}/repository/${pascal_name}Repository.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $entity_file"
        echo "  - $repo_file"
        return
    fi
    
    mkdir -p "${base_path}/domain"
    mkdir -p "${base_path}/repository"
    
    # Create entity
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{TABLE}}/${table}/g" \
        "${TEMPLATES_DIR}/entity.java.tmpl" > "$entity_file"
    
    # Create repository
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        "${TEMPLATES_DIR}/repository.java.tmpl" > "$repo_file"
    
    log_success "Created entity: $entity_file"
    log_success "Created repository: $repo_file"
    
    log_info "Don't forget to create a Flyway migration for the table!"
}

# -----------------------------------------------------------------------------
# Command: kafka-handler
# -----------------------------------------------------------------------------

cmd_kafka_handler() {
    local name=""
    local service=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]] || [[ -z "$service" ]]; then
        log_error "Both name and service are required"
        exit 1
    fi
    
    validate_service "$service"
    ensure_templates_dir
    
    local service_lower=$(echo "$service" | tr '[:upper:]' '[:lower:]')
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local topic_in="${service_lower}.${name_lower}.in"
    local topic_out="${service_lower}.${name_lower}.out"
    
    local base_package="${GROUP_ID}.${service_lower}"
    local base_path=""
    
    case "$service" in
        PERCEPTION)
            base_package="${GROUP_ID}.perception"
            base_path="${PROJECT_ROOT}/PERCEPTION/perception-api/src/main/java/${GROUP_ID//.//}/perception"
            ;;
        ODYSSEY)
            base_package="${GROUP_ID}.odyssey"
            base_path="${PROJECT_ROOT}/ODYSSEY/odyssey-core/src/main/java/${GROUP_ID//.//}/odyssey"
            ;;
        *)
            base_path="${PROJECT_ROOT}/${service}/src/main/java/${GROUP_ID//.//}/${service_lower}"
            ;;
    esac
    
    local handler_file="${base_path}/messaging/${pascal_name}KafkaHandler.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $handler_file"
        return
    fi
    
    mkdir -p "${base_path}/messaging"
    
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{SERVICE_LOWER}}/${service_lower}/g" \
        -e "s/{{TOPIC_IN}}/${topic_in}/g" \
        -e "s/{{TOPIC_OUT}}/${topic_out}/g" \
        "${TEMPLATES_DIR}/kafka-handler.java.tmpl" > "$handler_file"
    
    log_success "Created Kafka handler: $handler_file"
    log_info "Topics: $topic_in (in), $topic_out (out)"
}

# -----------------------------------------------------------------------------
# Command: migration
# -----------------------------------------------------------------------------

cmd_migration() {
    local name=""
    local service=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]] || [[ -z "$service" ]]; then
        log_error "Both name and service are required"
        exit 1
    fi
    
    validate_service "$service"
    
    local timestamp=$(date +%Y%m%d%H%M%S)
    local snake_name=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr ' ' '_' | tr '-' '_')
    local migration_name="V${timestamp}__${snake_name}.sql"
    
    local migrations_path=""
    case "$service" in
        PERCEPTION)
            migrations_path="${PROJECT_ROOT}/PERCEPTION/perception-api/src/main/resources/db/migration"
            ;;
        ODYSSEY)
            migrations_path="${PROJECT_ROOT}/ODYSSEY/odyssey-core/src/main/resources/db/migration"
            ;;
        *)
            migrations_path="${PROJECT_ROOT}/${service}/src/main/resources/db/migration"
            ;;
    esac
    
    local migration_file="${migrations_path}/${migration_name}"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $migration_file"
        return
    fi
    
    mkdir -p "$migrations_path"
    
    cat > "$migration_file" << EOF
-- Migration: ${name}
-- Service: ${service}
-- Created: $(date -Iseconds)

-- TODO: Add your SQL migration here
-- Example:
-- CREATE TABLE IF NOT EXISTS example (
--     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     name VARCHAR(255) NOT NULL,
--     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
--     updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
-- );
EOF

    log_success "Created migration: $migration_file"
}

# -----------------------------------------------------------------------------
# Command: test
# -----------------------------------------------------------------------------

cmd_test() {
    local name=""
    local service=""
    local type="integration"
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            -t|--type) type="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]] || [[ -z "$service" ]]; then
        log_error "Both name and service are required"
        exit 1
    fi
    
    validate_service "$service"
    ensure_templates_dir
    
    local service_lower=$(echo "$service" | tr '[:upper:]' '[:lower:]')
    local pascal_name=$(to_pascal_case "$name")
    local endpoint=$(to_snake_case "$name" | tr '_' '-')
    
    local test_package="${GROUP_ID}.${service_lower}"
    local test_path=""
    
    case "$service" in
        PERCEPTION)
            test_package="${GROUP_ID}.perception"
            test_path="${PROJECT_ROOT}/PERCEPTION/perception-api/src/test/java/${GROUP_ID//.//}/perception"
            ;;
        ODYSSEY)
            test_package="${GROUP_ID}.odyssey"
            test_path="${PROJECT_ROOT}/ODYSSEY/odyssey-core/src/test/java/${GROUP_ID//.//}/odyssey"
            ;;
        *)
            test_path="${PROJECT_ROOT}/${service}/src/test/java/${GROUP_ID//.//}/${service_lower}"
            ;;
    esac
    
    local test_file="${test_path}/${pascal_name}IntegrationTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $test_file"
        return
    fi
    
    mkdir -p "$test_path"
    
    sed -e "s/{{PACKAGE}}/${test_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{ENDPOINT}}/${endpoint}/g" \
        "${TEMPLATES_DIR}/integration-test.java.tmpl" > "$test_file"
    
    log_success "Created test: $test_file"
}

# -----------------------------------------------------------------------------
# Command: docs
# -----------------------------------------------------------------------------

show_docs_help() {
    cat << 'EOF'
USAGE:
    scaffold.sh docs <type> [options]

TYPES:
    readme      Generate a module README from template
    runbook     Generate an operational runbook from template
    adr         Generate an Architecture Decision Record (auto-numbered)
    api-guide   Generate an API guide from template

OPTIONS:
    -n, --name      Module or API family name (for readme, api-guide)
    -s, --service   Target service (e.g., PERCEPTION, ODYSSEY)
    -o, --output    Custom output path (optional)
    --title         Title for the document (for runbook, adr)
    --author        Author name(s) (for adr)
    --dry-run       Show what would be created without making changes

EXAMPLES:
    Module README:
        ./scripts/scaffold.sh docs readme --name MyModule --service PERCEPTION

    Operational Runbook:
        ./scripts/scaffold.sh docs runbook --title "Database Recovery" --service CAPSULE

    Architecture Decision Record:
        ./scripts/scaffold.sh docs adr --title "Adopt Event Sourcing" --author "Jane Doe"

    API Guide:
        ./scripts/scaffold.sh docs api-guide --name "Acquisition" --service PERCEPTION
EOF
}

get_next_adr_number() {
    local adr_dir="${PROJECT_ROOT}/docs/adr"
    local max_num=0
    
    if [[ -d "$adr_dir" ]]; then
        for file in "$adr_dir"/[0-9][0-9][0-9][0-9]-*.md; do
            if [[ -f "$file" ]]; then
                local basename=$(basename "$file")
                local num=$(echo "$basename" | grep -oE '^[0-9]+' | sed 's/^0*//')
                if [[ -z "$num" ]]; then
                    num=0
                fi
                if [[ "$num" -gt "$max_num" ]]; then
                    max_num="$num"
                fi
            fi
        done
    fi
    
    printf "%04d" $((max_num + 1))
}

process_template() {
    local template_file="$1"
    local output_file="$2"
    shift 2
    
    # Copy template to output
    cp "$template_file" "$output_file"
    
    # Process placeholder replacements passed as KEY=VALUE pairs
    while [[ $# -gt 0 ]]; do
        local key="${1%%=*}"
        local value="${1#*=}"
        # Use sed to replace placeholders
        sed -i "s|{${key}}|${value}|g" "$output_file"
        shift
    done
}

cmd_docs() {
    if [[ $# -eq 0 ]]; then
        show_docs_help
        exit 0
    fi
    
    local doc_type="$1"
    shift
    
    case "$doc_type" in
        readme)    cmd_docs_readme "$@" ;;
        runbook)   cmd_docs_runbook "$@" ;;
        adr)       cmd_docs_adr "$@" ;;
        api-guide) cmd_docs_api_guide "$@" ;;
        help|--help|-h) show_docs_help ;;
        *)
            log_error "Unknown docs type: $doc_type"
            show_docs_help
            exit 1
            ;;
    esac
}

cmd_docs_readme() {
    local name=""
    local service=""
    local output=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            -o|--output) output="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Module name is required (--name)"
        exit 1
    fi
    
    local template_file="${PROJECT_ROOT}/docs/templates/module-readme.md"
    
    if [[ ! -f "$template_file" ]]; then
        log_error "Template not found: $template_file"
        exit 1
    fi
    
    # Determine output path
    if [[ -z "$output" ]]; then
        if [[ -n "$service" ]]; then
            validate_service "$service"
            output="${PROJECT_ROOT}/${service}/${name}/README.md"
        else
            output="${PROJECT_ROOT}/${name}/README.md"
        fi
    fi
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $output"
        log_info "  Template: $template_file"
        log_info "  Replacements: MODULE_NAME=$name"
        return
    fi
    
    # Ensure output directory exists
    mkdir -p "$(dirname "$output")"
    
    # Process template
    process_template "$template_file" "$output" \
        "MODULE_NAME=$name"
    
    log_success "Created module README: $output"
}

cmd_docs_runbook() {
    local title=""
    local service=""
    local output=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --title) title="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            -o|--output) output="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$title" ]]; then
        log_error "Runbook title is required (--title)"
        exit 1
    fi
    
    local template_file="${PROJECT_ROOT}/docs/templates/runbook.md"
    
    if [[ ! -f "$template_file" ]]; then
        log_error "Template not found: $template_file"
        exit 1
    fi
    
    # Generate filename from title
    local filename=$(echo "$title" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd '[:alnum:]-')
    local current_date=$(date +%Y-%m-%d)
    
    # Determine output path
    if [[ -z "$output" ]]; then
        if [[ -n "$service" ]]; then
            validate_service "$service"
            output="${PROJECT_ROOT}/${service}/docs/runbooks/${filename}.md"
        else
            output="${PROJECT_ROOT}/docs/operations/runbooks/${filename}.md"
        fi
    fi
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $output"
        log_info "  Template: $template_file"
        log_info "  Replacements: RUNBOOK_TITLE=$title, DATE=$current_date"
        return
    fi
    
    # Ensure output directory exists
    mkdir -p "$(dirname "$output")"
    
    # Process template
    process_template "$template_file" "$output" \
        "RUNBOOK_TITLE=$title" \
        "DATE=$current_date"
    
    log_success "Created runbook: $output"
}

cmd_docs_adr() {
    local title=""
    local author=""
    local output=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --title) title="$2"; shift 2 ;;
            --author) author="$2"; shift 2 ;;
            -o|--output) output="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$title" ]]; then
        log_error "ADR title is required (--title)"
        exit 1
    fi
    
    local template_file="${PROJECT_ROOT}/docs/templates/adr.md"
    
    if [[ ! -f "$template_file" ]]; then
        log_error "Template not found: $template_file"
        exit 1
    fi
    
    # Auto-generate ADR number
    local adr_number=$(get_next_adr_number)
    local current_date=$(date +%Y-%m-%d)
    
    # Generate filename from title
    local filename=$(echo "$title" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd '[:alnum:]-')
    
    # Determine output path
    if [[ -z "$output" ]]; then
        output="${PROJECT_ROOT}/docs/adr/${adr_number}-${filename}.md"
    fi
    
    # Default author if not provided
    if [[ -z "$author" ]]; then
        author="$(git config user.name 2>/dev/null || echo 'Unknown')"
    fi
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $output"
        log_info "  Template: $template_file"
        log_info "  Replacements: NUMBER=$adr_number, TITLE=$title, DATE=$current_date, AUTHOR_NAMES=$author"
        return
    fi
    
    # Ensure output directory exists
    mkdir -p "$(dirname "$output")"
    
    # Process template
    process_template "$template_file" "$output" \
        "NUMBER=$adr_number" \
        "TITLE=$title" \
        "DATE=$current_date" \
        "AUTHOR_NAMES=$author" \
        "REVIEWER_NAMES=TBD" \
        "AUTHOR=$author"
    
    log_success "Created ADR: $output"
    log_info "ADR Number: $adr_number"
}

cmd_docs_api_guide() {
    local name=""
    local service=""
    local output=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--service) service="$2"; shift 2 ;;
            -o|--output) output="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "API family name is required (--name)"
        exit 1
    fi
    
    local template_file="${PROJECT_ROOT}/docs/templates/api-guide.md"
    
    if [[ ! -f "$template_file" ]]; then
        log_error "Template not found: $template_file"
        exit 1
    fi
    
    # Generate filename from name
    local filename=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd '[:alnum:]-')
    local current_date=$(date +%Y-%m-%d)
    
    # Determine output path
    if [[ -z "$output" ]]; then
        if [[ -n "$service" ]]; then
            validate_service "$service"
            output="${PROJECT_ROOT}/${service}/docs/api/${filename}-api-guide.md"
        else
            output="${PROJECT_ROOT}/docs/api/${filename}-api-guide.md"
        fi
    fi
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create: $output"
        log_info "  Template: $template_file"
        log_info "  Replacements: API_FAMILY=$name, DATE=$current_date"
        return
    fi
    
    # Ensure output directory exists
    mkdir -p "$(dirname "$output")"
    
    # Process template
    process_template "$template_file" "$output" \
        "API_FAMILY=$name" \
        "DATE=$current_date"
    
    log_success "Created API guide: $output"
}

# -----------------------------------------------------------------------------
# Command: connector (PERCEPTION-specific)
# -----------------------------------------------------------------------------

cmd_connector() {
    local name=""
    local type=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -t|--type) type="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Connector name is required (--name)"
        exit 1
    fi
    
    if [[ -z "$type" ]]; then
        type=$(echo "$name" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
        log_info "Using default type: $type"
    fi
    
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local name_camel=$(to_camel_case "$name")
    local type_lower=$(echo "$type" | tr '[:upper:]' '[:lower:]')
    
    local base_package="com.z254.butterfly.perception.acquisition"
    local base_path="${PROJECT_ROOT}/PERCEPTION/perception-acquisition/src/main/java/com/z254/butterfly/perception/acquisition"
    local test_path="${PROJECT_ROOT}/PERCEPTION/perception-acquisition/src/test/java/com/z254/butterfly/perception/acquisition"
    
    local fetch_processor="${base_path}/route/processor/${pascal_name}FetchProcessor.java"
    local page_processor="${base_path}/route/processor/${pascal_name}PageProcessor.java"
    local config_file="${base_path}/config/${pascal_name}ConnectorConfig.java"
    local test_file="${test_path}/route/processor/${pascal_name}ProcessorTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $fetch_processor"
        echo "  - $page_processor"
        echo "  - $config_file"
        echo "  - $test_file"
        return
    fi
    
    # Check templates exist
    local templates_dir="${PROJECT_ROOT}/scripts/templates/connector"
    if [[ ! -d "$templates_dir" ]]; then
        log_error "Connector templates not found at: $templates_dir"
        log_info "Run scaffold.sh to initialize templates first"
        exit 1
    fi
    
    mkdir -p "${base_path}/route/processor"
    mkdir -p "${base_path}/config"
    mkdir -p "${test_path}/route/processor"
    
    # Generate files from templates
    log_info "Generating ${pascal_name} connector..."
    
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{NAME_CAMEL}}/${name_camel}/g" \
        -e "s/{{TYPE}}/${type}/g" \
        -e "s/{{TYPE_LOWER}}/${type_lower}/g" \
        "${templates_dir}/fetch-processor.java.tmpl" > "$fetch_processor"
    log_success "Created: $fetch_processor"
    
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{NAME_CAMEL}}/${name_camel}/g" \
        -e "s/{{TYPE}}/${type}/g" \
        -e "s/{{TYPE_LOWER}}/${type_lower}/g" \
        "${templates_dir}/page-processor.java.tmpl" > "$page_processor"
    log_success "Created: $page_processor"
    
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{NAME_CAMEL}}/${name_camel}/g" \
        -e "s/{{TYPE}}/${type}/g" \
        -e "s/{{TYPE_LOWER}}/${type_lower}/g" \
        "${templates_dir}/config.java.tmpl" > "$config_file"
    log_success "Created: $config_file"
    
    sed -e "s/{{PACKAGE}}/${base_package}/g" \
        -e "s/{{NAME}}/${pascal_name}/g" \
        -e "s/{{NAME_LOWER}}/${name_lower}/g" \
        -e "s/{{NAME_CAMEL}}/${name_camel}/g" \
        -e "s/{{TYPE}}/${type}/g" \
        -e "s/{{TYPE_LOWER}}/${type_lower}/g" \
        "${templates_dir}/processor-test.java.tmpl" > "$test_file"
    log_success "Created: $test_file"
    
    log_success "Connector ${pascal_name} scaffolded successfully!"
    log_info "Next steps:"
    echo "  1. Add source type '${type}' to Source.SourceType enum"
    echo "  2. Implement fetch logic in ${pascal_name}FetchProcessor"
    echo "  3. Implement conversion logic in ${pascal_name}PageProcessor"
    echo "  4. Create Camel route template for the connector"
    echo "  5. Register the route in RouteManager"
    echo "  6. Document in ACQUISITION_CONNECTORS.md"
}

# -----------------------------------------------------------------------------
# Command: signal-detector (PERCEPTION-specific)
# -----------------------------------------------------------------------------

cmd_signal_detector() {
    local name=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Detector name is required (--name)"
        exit 1
    fi
    
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local name_camel=$(to_camel_case "$name")
    
    local base_package="com.z254.butterfly.perception.signals"
    local base_path="${PROJECT_ROOT}/PERCEPTION/perception-signals/src/main/java/com/z254/butterfly/perception/signals"
    local test_path="${PROJECT_ROOT}/PERCEPTION/perception-signals/src/test/java/com/z254/butterfly/perception/signals"
    
    local detector_file="${base_path}/detector/impl/${pascal_name}Detector.java"
    local config_file="${base_path}/detector/impl/${pascal_name}DetectorConfig.java"
    local result_file="${base_path}/detector/impl/${pascal_name}DetectionResult.java"
    local test_file="${test_path}/detector/impl/${pascal_name}DetectorTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $detector_file"
        echo "  - $config_file"
        echo "  - $result_file"
        echo "  - $test_file"
        return
    fi
    
    mkdir -p "${base_path}/detector/impl"
    mkdir -p "${test_path}/detector/impl"
    
    log_info "Generating ${pascal_name}Detector..."
    
    # Generate detector
    cat > "$detector_file" << EOF
package ${base_package}.detector.impl;

import ${base_package}.detector.DetectionContext;
import ${base_package}.detector.SignalDetector;
import ${base_package}.store.SignalsStateStore;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ${pascal_name} signal detector.
 * <p>
 * TODO: Describe what this detector identifies and when it triggers alerts.
 * </p>
 *
 * @see ${pascal_name}DetectorConfig
 * @see ${pascal_name}DetectionResult
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ${pascal_name}Detector implements SignalDetector<${pascal_name}DetectorConfig, ${pascal_name}DetectionResult> {

    private final SignalsStateStore stateStore;
    private final MeterRegistry meterRegistry;

    @Getter
    @Setter
    private boolean enabled = true;

    @Override
    public String getName() {
        return "${name_lower}";
    }

    @Override
    public ${pascal_name}DetectorConfig getDefaultConfig() {
        return new ${pascal_name}DetectorConfig();
    }

    @Override
    public ${pascal_name}DetectionResult detect(DetectionContext context) {
        if (!enabled) {
            return ${pascal_name}DetectionResult.empty();
        }

        log.debug("Running ${name_lower} detection for context: {}", context);

        // TODO: Implement detection logic
        // 1. Query state store for relevant data
        // 2. Apply detection algorithm
        // 3. Build and return result

        return ${pascal_name}DetectionResult.builder()
                .hasSignals(false)
                .build();
    }

    @Override
    public Class<${pascal_name}DetectorConfig> getConfigClass() {
        return ${pascal_name}DetectorConfig.class;
    }
}
EOF
    log_success "Created: $detector_file"
    
    # Generate config
    cat > "$config_file" << EOF
package ${base_package}.detector.impl;

import ${base_package}.detector.BaseDetectorConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for ${pascal_name}Detector.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${pascal_name}DetectorConfig extends BaseDetectorConfig {

    /**
     * Detection threshold (0.0 - 1.0).
     */
    private double threshold = 0.5;

    /**
     * Minimum samples required before detection.
     */
    private int minSamples = 10;

    /**
     * Time window for detection in hours.
     */
    private int windowHours = 24;
}
EOF
    log_success "Created: $config_file"
    
    # Generate result
    cat > "$result_file" << EOF
package ${base_package}.detector.impl;

import ${base_package}.detector.BaseDetectionResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Detection result from ${pascal_name}Detector.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${pascal_name}DetectionResult extends BaseDetectionResult {

    private double score;
    private List<String> affectedSources;
    private Map<String, Object> details;

    @Builder
    public ${pascal_name}DetectionResult(boolean hasSignals, double score,
                                         List<String> affectedSources,
                                         Map<String, Object> details) {
        super(hasSignals);
        this.score = score;
        this.affectedSources = affectedSources != null ? affectedSources : List.of();
        this.details = details != null ? details : Map.of();
    }

    public static ${pascal_name}DetectionResult empty() {
        return ${pascal_name}DetectionResult.builder()
                .hasSignals(false)
                .score(0.0)
                .build();
    }
}
EOF
    log_success "Created: $result_file"
    
    # Generate test
    cat > "$test_file" << EOF
package ${base_package}.detector.impl;

import ${base_package}.detector.DetectionContext;
import ${base_package}.store.SignalsStateStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("${pascal_name}Detector")
class ${pascal_name}DetectorTest {

    @Mock
    private SignalsStateStore stateStore;

    private SimpleMeterRegistry meterRegistry;
    private ${pascal_name}Detector detector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        detector = new ${pascal_name}Detector(stateStore, meterRegistry);
    }

    @Test
    @DisplayName("should return empty result when disabled")
    void shouldReturnEmptyWhenDisabled() {
        detector.setEnabled(false);

        var result = detector.detect(DetectionContext.forLastHours(24));

        assertThat(result.hasSignals()).isFalse();
    }

    @Test
    @DisplayName("should return detector name")
    void shouldReturnDetectorName() {
        assertThat(detector.getName()).isEqualTo("${name_lower}");
    }

    @Test
    @DisplayName("should return default config")
    void shouldReturnDefaultConfig() {
        var config = detector.getDefaultConfig();

        assertThat(config).isNotNull();
        assertThat(config.getThreshold()).isEqualTo(0.5);
    }

    // TODO: Add more tests for detection logic
}
EOF
    log_success "Created: $test_file"
    
    log_success "Signal detector ${pascal_name} scaffolded successfully!"
    log_info "Next steps:"
    echo "  1. Implement detection logic in ${pascal_name}Detector.detect()"
    echo "  2. Add configuration properties to SignalsProperties"
    echo "  3. Write tests for your detection algorithm"
    echo "  4. Document in PERCEPTION/docs/guides/ADDING_A_SIGNAL_DETECTOR.md"
}

# -----------------------------------------------------------------------------
# Command: scenario (PERCEPTION-specific)
# -----------------------------------------------------------------------------

cmd_scenario() {
    local name=""
    local school=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            -s|--school) school="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Scenario name is required (--name)"
        exit 1
    fi
    
    if [[ -z "$school" ]]; then
        school="CUSTOM"
        log_info "Using default school: $school"
    fi
    
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local name_camel=$(to_camel_case "$name")
    
    local base_package="com.z254.butterfly.perception.scenarios"
    local base_path="${PROJECT_ROOT}/PERCEPTION/perception-scenarios/src/main/java/com/z254/butterfly/perception/scenarios"
    local test_path="${PROJECT_ROOT}/PERCEPTION/perception-scenarios/src/test/java/com/z254/butterfly/perception/scenarios"
    
    local school_file="${base_path}/pluralism/impl/${pascal_name}School.java"
    local service_file="${base_path}/service/impl/${pascal_name}ScenarioService.java"
    local test_file="${test_path}/service/impl/${pascal_name}ScenarioServiceTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $school_file"
        echo "  - $service_file"
        echo "  - $test_file"
        return
    fi
    
    mkdir -p "${base_path}/pluralism/impl"
    mkdir -p "${base_path}/service/impl"
    mkdir -p "${test_path}/service/impl"
    
    log_info "Generating ${pascal_name} scenario components..."
    
    # Generate school
    cat > "$school_file" << EOF
package ${base_package}.pluralism.impl;

import ${base_package}.model.StackableScenario;
import ${base_package}.pluralism.ScenarioGenerationSchool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ${pascal_name} scenario generation school.
 * <p>
 * Implements the ${school} approach to scenario generation.
 * </p>
 *
 * @see ScenarioGenerationSchool
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ${pascal_name}School implements ScenarioGenerationSchool {

    @Override
    public String getName() {
        return "${name_lower}";
    }

    @Override
    public String getDescription() {
        return "${pascal_name} scenario generation using ${school} methodology";
    }

    @Override
    public List<StackableScenario> generateScenarios(Map<String, Object> context) {
        log.debug("Generating scenarios with ${pascal_name} school for context: {}", context);

        // TODO: Implement scenario generation logic
        // 1. Extract relevant context parameters
        // 2. Apply generation algorithm
        // 3. Return list of generated scenarios

        return List.of();
    }

    @Override
    public double getDefaultWeight() {
        return 1.0;
    }

    @Override
    public boolean supports(Map<String, Object> context) {
        // TODO: Implement support check based on context
        return true;
    }
}
EOF
    log_success "Created: $school_file"
    
    # Generate service
    cat > "$service_file" << EOF
package ${base_package}.service.impl;

import ${base_package}.model.StackableScenario;
import ${base_package}.pluralism.impl.${pascal_name}School;
import ${base_package}.repository.ScenarioRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for ${pascal_name} scenario management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ${pascal_name}ScenarioService {

    private final ${pascal_name}School school;
    private final ScenarioRepository repository;
    private final MeterRegistry meterRegistry;

    /**
     * Generate scenarios using the ${pascal_name} school.
     *
     * @param context generation context parameters
     * @return list of generated scenarios
     */
    public List<StackableScenario> generate(Map<String, Object> context) {
        log.info("Generating ${name_lower} scenarios with context: {}", context);

        List<StackableScenario> scenarios = school.generateScenarios(context);

        meterRegistry.counter("scenarios.${name_lower}.generated")
                .increment(scenarios.size());

        return scenarios;
    }

    /**
     * Check if the school supports the given context.
     *
     * @param context the context to check
     * @return true if supported
     */
    public boolean supports(Map<String, Object> context) {
        return school.supports(context);
    }
}
EOF
    log_success "Created: $service_file"
    
    # Generate test
    cat > "$test_file" << EOF
package ${base_package}.service.impl;

import ${base_package}.pluralism.impl.${pascal_name}School;
import ${base_package}.repository.ScenarioRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("${pascal_name}ScenarioService")
class ${pascal_name}ScenarioServiceTest {

    @Mock
    private ScenarioRepository repository;

    private ${pascal_name}School school;
    private SimpleMeterRegistry meterRegistry;
    private ${pascal_name}ScenarioService service;

    @BeforeEach
    void setUp() {
        school = new ${pascal_name}School();
        meterRegistry = new SimpleMeterRegistry();
        service = new ${pascal_name}ScenarioService(school, repository, meterRegistry);
    }

    @Test
    @DisplayName("should check support for context")
    void shouldCheckSupportForContext() {
        var context = Map.<String, Object>of("key", "value");

        boolean supported = service.supports(context);

        assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("should generate scenarios")
    void shouldGenerateScenarios() {
        var context = Map.<String, Object>of("key", "value");

        var scenarios = service.generate(context);

        assertThat(scenarios).isNotNull();
    }

    // TODO: Add more tests for scenario generation logic
}
EOF
    log_success "Created: $test_file"
    
    log_success "Scenario ${pascal_name} scaffolded successfully!"
    log_info "Next steps:"
    echo "  1. Implement generation logic in ${pascal_name}School.generateScenarios()"
    echo "  2. Define support criteria in ${pascal_name}School.supports()"
    echo "  3. Register the school with MultiModelScenarioService"
    echo "  4. Write tests for your generation algorithm"
    echo "  5. Document in PERCEPTION/docs/guides/ADDING_A_SCENARIO_SCHOOL.md"
}

# -----------------------------------------------------------------------------
# Command: reasoning-rule (PERCEPTION-specific)
# -----------------------------------------------------------------------------

cmd_reasoning_rule() {
    local name=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Rule name is required (--name)"
        exit 1
    fi
    
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local name_camel=$(to_camel_case "$name")
    
    local base_package="com.z254.butterfly.perception.reasoning"
    local base_path="${PROJECT_ROOT}/PERCEPTION/perception-reasoning/src/main/java/com/z254/butterfly/perception/reasoning"
    local test_path="${PROJECT_ROOT}/PERCEPTION/perception-reasoning/src/test/java/com/z254/butterfly/perception/reasoning"
    
    local rule_file="${base_path}/rule/impl/${pascal_name}Rule.java"
    local config_file="${base_path}/rule/impl/${pascal_name}RuleConfig.java"
    local test_file="${test_path}/rule/impl/${pascal_name}RuleTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $rule_file"
        echo "  - $config_file"
        echo "  - $test_file"
        return
    fi
    
    mkdir -p "${base_path}/rule/impl"
    mkdir -p "${test_path}/rule/impl"
    
    log_info "Generating ${pascal_name}Rule..."
    
    # Generate rule
    cat > "$rule_file" << EOF
package ${base_package}.rule.impl;

import ${base_package}.ReasoningContext;
import ${base_package}.rule.ReasoningRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ${pascal_name} reasoning rule.
 * <p>
 * TODO: Describe what this rule evaluates and how it adjusts plausibility.
 * </p>
 *
 * @see ${pascal_name}RuleConfig
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ${pascal_name}Rule implements ReasoningRule {

    private final MeterRegistry meterRegistry;

    @Getter
    @Setter
    private ${pascal_name}RuleConfig config = new ${pascal_name}RuleConfig();

    @Getter
    @Setter
    private boolean enabled = true;

    // Metrics
    private Counter applicationsCounter;

    @Override
    public String getName() {
        return "${name_lower}";
    }

    @Override
    public String getDescription() {
        return "${pascal_name} reasoning rule - TODO: add description";
    }

    @Override
    public int getPriority() {
        return 100;  // Adjust as needed
    }

    @Override
    public boolean appliesTo(String interpretation, ReasoningContext context) {
        if (!enabled || interpretation == null || interpretation.isBlank()) {
            return false;
        }
        // TODO: Implement applicability check
        return true;
    }

    @Override
    public double apply(String interpretation, double currentPlausibility, ReasoningContext context) {
        getApplicationsCounter().increment();

        log.debug("Applying ${name_lower} rule to: {}",
            interpretation.substring(0, Math.min(100, interpretation.length())));

        // TODO: Implement plausibility adjustment logic
        // Example patterns:
        // - Penalty: return Math.max(0.0, currentPlausibility - penalty);
        // - Boost: return Math.min(1.0, currentPlausibility + boost);
        // - Scale: return currentPlausibility * confidenceFactor;

        return currentPlausibility;
    }

    private Counter getApplicationsCounter() {
        if (applicationsCounter == null) {
            applicationsCounter = Counter.builder("reasoning.${name_lower}.applications")
                .description("Total ${name_lower} rule applications")
                .register(meterRegistry);
        }
        return applicationsCounter;
    }
}
EOF
    log_success "Created: $rule_file"
    
    # Generate config
    cat > "$config_file" << EOF
package ${base_package}.rule.impl;

import ${base_package}.rule.BaseRuleConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for ${pascal_name}Rule.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${pascal_name}RuleConfig extends BaseRuleConfig {

    /**
     * Maximum penalty to apply (0.0 - 1.0).
     */
    private double maxPenalty = 0.5;

    /**
     * Maximum boost to apply (0.0 - 1.0).
     */
    private double maxBoost = 0.2;

    /**
     * Threshold for triggering the rule.
     */
    private double threshold = 0.5;

    /**
     * Whether to apply strict evaluation.
     */
    private boolean strictMode = false;
}
EOF
    log_success "Created: $config_file"
    
    # Generate test
    cat > "$test_file" << EOF
package ${base_package}.rule.impl;

import ${base_package}.ReasoningContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("${pascal_name}Rule")
class ${pascal_name}RuleTest {

    private SimpleMeterRegistry meterRegistry;
    private ${pascal_name}Rule rule;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        rule = new ${pascal_name}Rule(meterRegistry);
    }

    @Nested
    @DisplayName("appliesTo")
    class AppliesTo {

        @Test
        @DisplayName("should not apply when disabled")
        void shouldNotApplyWhenDisabled() {
            rule.setEnabled(false);
            var context = ReasoningContext.empty();

            assertThat(rule.appliesTo("test interpretation", context)).isFalse();
        }

        @Test
        @DisplayName("should not apply to empty interpretation")
        void shouldNotApplyToEmptyInterpretation() {
            var context = ReasoningContext.empty();

            assertThat(rule.appliesTo("", context)).isFalse();
            assertThat(rule.appliesTo(null, context)).isFalse();
        }

        @Test
        @DisplayName("should apply to valid interpretation")
        void shouldApplyToValidInterpretation() {
            var context = ReasoningContext.empty();

            assertThat(rule.appliesTo("valid interpretation", context)).isTrue();
        }
    }

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        @DisplayName("should return plausibility in valid range")
        void shouldReturnValidPlausibility() {
            var context = ReasoningContext.empty();
            double initialPlausibility = 0.5;

            double result = rule.apply("test interpretation", initialPlausibility, context);

            assertThat(result).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("should record metrics")
        void shouldRecordMetrics() {
            var context = ReasoningContext.empty();

            rule.apply("test interpretation", 0.5, context);

            assertThat(meterRegistry.counter("reasoning.${name_lower}.applications").count())
                .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("should return rule name")
    void shouldReturnRuleName() {
        assertThat(rule.getName()).isEqualTo("${name_lower}");
    }

    @Test
    @DisplayName("should return description")
    void shouldReturnDescription() {
        assertThat(rule.getDescription()).isNotBlank();
    }
}
EOF
    log_success "Created: $test_file"
    
    log_success "Reasoning rule ${pascal_name} scaffolded successfully!"
    log_info "Next steps:"
    echo "  1. Implement appliesTo() logic in ${pascal_name}Rule"
    echo "  2. Implement apply() logic for plausibility adjustment"
    echo "  3. Add configuration properties to application.yml"
    echo "  4. Write tests for your rule logic"
    echo "  5. Document in PERCEPTION/docs/guides/ADDING_A_REASONING_RULE.md"
}

# -----------------------------------------------------------------------------
# Command: scenario-type (PERCEPTION-specific)
# -----------------------------------------------------------------------------

cmd_scenario_type() {
    local name=""
    local dry_run=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -n|--name) name="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done
    
    if [[ -z "$name" ]]; then
        log_error "Handler name is required (--name)"
        exit 1
    fi
    
    local pascal_name=$(to_pascal_case "$name")
    local name_lower=$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr '-' '_')
    local name_upper=$(echo "$name" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
    local name_camel=$(to_camel_case "$name")
    
    local base_package="com.z254.butterfly.perception.scenarios"
    local base_path="${PROJECT_ROOT}/PERCEPTION/perception-scenarios/src/main/java/com/z254/butterfly/perception/scenarios"
    local test_path="${PROJECT_ROOT}/PERCEPTION/perception-scenarios/src/test/java/com/z254/butterfly/perception/scenarios"
    
    local handler_file="${base_path}/handler/impl/${pascal_name}Handler.java"
    local config_file="${base_path}/handler/impl/${pascal_name}Config.java"
    local test_file="${test_path}/handler/impl/${pascal_name}HandlerTest.java"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would create:"
        echo "  - $handler_file"
        echo "  - $config_file"
        echo "  - $test_file"
        return
    fi
    
    mkdir -p "${base_path}/handler/impl"
    mkdir -p "${test_path}/handler/impl"
    
    log_info "Generating ${pascal_name}Handler..."
    
    # Generate handler
    cat > "$handler_file" << EOF
package ${base_package}.handler.impl;

import ${base_package}.handler.ProcessingContext;
import ${base_package}.handler.ScenarioTypeHandler;
import ${base_package}.handler.ValidationResult;
import ${base_package}.model.ScenarioType;
import ${base_package}.model.StackableScenario;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Handler for ${name_upper} scenario types.
 * <p>
 * TODO: Describe what this handler validates and processes.
 * </p>
 *
 * @see ${pascal_name}Config
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ${pascal_name}Handler implements ScenarioTypeHandler {

    private final MeterRegistry meterRegistry;

    @Getter
    @Setter
    private ${pascal_name}Config config = new ${pascal_name}Config();

    // Metrics
    private Counter processedCounter;
    private Counter validationFailedCounter;

    @Override
    public ScenarioType getType() {
        // TODO: Return the appropriate ScenarioType enum value
        // You may need to add ${name_upper} to the ScenarioType enum first
        return ScenarioType.OPERATIONAL;  // Placeholder - update this
    }

    @Override
    public ValidationResult validate(StackableScenario scenario) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // TODO: Implement validation logic
        // Example validations:
        // - Check required fields in metadata
        // - Validate confidence thresholds
        // - Check for mandatory relationships

        Map<String, Object> metadata = scenario.getMetadata();
        for (String field : config.getRequiredFields()) {
            if (!metadata.containsKey(field) || metadata.get(field) == null) {
                errors.add("Missing required field: " + field);
            }
        }

        if (!errors.isEmpty()) {
            getValidationFailedCounter().increment();
        }

        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .build();
    }

    @Override
    public StackableScenario process(StackableScenario scenario, ProcessingContext context) {
        getProcessedCounter().increment();

        log.debug("Processing ${name_lower} scenario: {}", scenario.getId());

        // Create mutable copy
        StackableScenario processed = scenario.toBuilder().build();
        Map<String, Object> metadata = new HashMap<>(processed.getMetadata());

        // Add processing metadata
        metadata.put("processedAt", Instant.now());
        metadata.put("processedBy", getType().name());

        // TODO: Add type-specific processing
        // Example: Calculate scores, add derived fields, etc.

        processed.setMetadata(metadata);
        return processed;
    }

    @Override
    public Map<String, Object> enrich(StackableScenario scenario) {
        Map<String, Object> enrichment = new HashMap<>();

        // TODO: Add type-specific enrichment
        enrichment.put("scenarioCategory", "${name_lower}");
        enrichment.put("handlerVersion", "1.0");

        return enrichment;
    }

    @Override
    public double adjustConfidence(StackableScenario scenario) {
        double confidence = scenario.getConfidence();

        // TODO: Apply type-specific confidence adjustments
        // Example: Boost for validated scenarios, penalize for stale data

        return confidence;
    }

    // ==========================================================================
    // Metrics
    // ==========================================================================

    private Counter getProcessedCounter() {
        if (processedCounter == null) {
            processedCounter = Counter.builder("scenarios.${name_lower}.processed")
                .description("Total ${name_lower} scenarios processed")
                .register(meterRegistry);
        }
        return processedCounter;
    }

    private Counter getValidationFailedCounter() {
        if (validationFailedCounter == null) {
            validationFailedCounter = Counter.builder("scenarios.${name_lower}.validation_failed")
                .description("Total ${name_lower} scenario validation failures")
                .register(meterRegistry);
        }
        return validationFailedCounter;
    }
}
EOF
    log_success "Created: $handler_file"
    
    # Generate config
    cat > "$config_file" << EOF
package ${base_package}.handler.impl;

import ${base_package}.handler.BaseHandlerConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration for ${pascal_name}Handler.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${pascal_name}Config extends BaseHandlerConfig {

    /**
     * Required metadata fields for this scenario type.
     */
    private List<String> requiredFields = List.of();

    /**
     * Minimum confidence threshold for processing.
     */
    private double minConfidence = 0.5;

    /**
     * Whether to require approval before activation.
     */
    private boolean requiresApproval = false;

    /**
     * Maximum days until scenario review is required.
     */
    private int maxDaysUntilReview = 90;
}
EOF
    log_success "Created: $config_file"
    
    # Generate test
    cat > "$test_file" << EOF
package ${base_package}.handler.impl;

import ${base_package}.handler.ProcessingContext;
import ${base_package}.handler.ValidationResult;
import ${base_package}.model.StackableScenario;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("${pascal_name}Handler")
class ${pascal_name}HandlerTest {

    private SimpleMeterRegistry meterRegistry;
    private ${pascal_name}Handler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new ${pascal_name}Handler(meterRegistry);
    }

    @Test
    @DisplayName("should return scenario type")
    void shouldReturnScenarioType() {
        assertThat(handler.getType()).isNotNull();
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("should pass validation for valid scenario")
        void shouldPassForValidScenario() {
            StackableScenario scenario = createValidScenario();

            ValidationResult result = handler.validate(scenario);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("process")
    class Process {

        @Test
        @DisplayName("should add processing metadata")
        void shouldAddProcessingMetadata() {
            StackableScenario scenario = createValidScenario();

            StackableScenario processed = handler.process(scenario, ProcessingContext.empty());

            assertThat(processed.getMetadata().get("processedAt")).isNotNull();
        }
    }

    @Nested
    @DisplayName("enrich")
    class Enrich {

        @Test
        @DisplayName("should add enrichment data")
        void shouldAddEnrichmentData() {
            StackableScenario scenario = createValidScenario();

            Map<String, Object> enrichment = handler.enrich(scenario);

            assertThat(enrichment).isNotEmpty();
            assertThat(enrichment.get("scenarioCategory")).isEqualTo("${name_lower}");
        }
    }

    @Nested
    @DisplayName("adjustConfidence")
    class AdjustConfidence {

        @Test
        @DisplayName("should return valid confidence")
        void shouldReturnValidConfidence() {
            StackableScenario scenario = createValidScenario();

            double adjusted = handler.adjustConfidence(scenario);

            assertThat(adjusted).isBetween(0.0, 1.0);
        }
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    private StackableScenario createValidScenario() {
        Map<String, Object> metadata = new HashMap<>();
        // TODO: Add required metadata fields

        return StackableScenario.builder()
            .id("scenario-test-123")
            .title("Test ${pascal_name} Scenario")
            .description("A test scenario for ${pascal_name}Handler")
            .confidence(0.85)
            .impactScore(0.6)
            .metadata(metadata)
            .build();
    }
}
EOF
    log_success "Created: $test_file"
    
    log_success "Scenario type handler ${pascal_name} scaffolded successfully!"
    log_info "Next steps:"
    echo "  1. Add ${name_upper} to ScenarioType enum (if new type)"
    echo "  2. Update getType() to return the correct ScenarioType"
    echo "  3. Implement validate() with type-specific rules"
    echo "  4. Implement process() with type-specific processing"
    echo "  5. Add configuration properties to application.yml"
    echo "  6. Write tests for your handler logic"
    echo "  7. Document in PERCEPTION/docs/guides/ADDING_A_SCENARIO_TYPE.md"
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    if [[ $# -eq 0 ]]; then
        show_help
        exit 0
    fi
    
    local command="$1"
    shift
    
    case "$command" in
        service)         cmd_service "$@" ;;
        controller)      cmd_controller "$@" ;;
        entity)          cmd_entity "$@" ;;
        kafka-handler)   cmd_kafka_handler "$@" ;;
        migration)       cmd_migration "$@" ;;
        test)            cmd_test "$@" ;;
        docs)            cmd_docs "$@" ;;
        connector)       cmd_connector "$@" ;;
        signal-detector) cmd_signal_detector "$@" ;;
        reasoning-rule)  cmd_reasoning_rule "$@" ;;
        scenario-type)   cmd_scenario_type "$@" ;;
        scenario)        cmd_scenario "$@" ;;
        help|--help|-h)  show_help ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"

