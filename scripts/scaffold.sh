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
#   service       Create a new microservice skeleton
#   controller    Add a REST controller to an existing service
#   entity        Add a JPA entity with repository
#   kafka-handler Add a Kafka consumer/producer handler
#   migration     Add a Flyway migration
#   test          Add test scaffolding (unit, integration, chaos)
#
# Examples:
#   ./scripts/scaffold.sh service --name market-data --port 8090
#   ./scripts/scaffold.sh controller --service PERCEPTION --name SignalController
#   ./scripts/scaffold.sh entity --service ODYSSEY --name Actor --table actors
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
    help            Show this help message

OPTIONS:
    -n, --name      Name of the component (required for most commands)
    -s, --service   Target service (e.g., PERCEPTION, ODYSSEY)
    -p, --port      Port number (for service creation)
    -t, --table     Database table name (for entity)
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
        service)       cmd_service "$@" ;;
        controller)    cmd_controller "$@" ;;
        entity)        cmd_entity "$@" ;;
        kafka-handler) cmd_kafka_handler "$@" ;;
        migration)     cmd_migration "$@" ;;
        test)          cmd_test "$@" ;;
        help|--help|-h) show_help ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"

