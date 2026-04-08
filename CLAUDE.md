# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SageLine is a Spring Boot 4.0.2 application for production line quality validation with AI-powered non-conformity prediction and KPI tracking. It uses PostgreSQL and integrates with an external Python ML service for predictions.

## Build & Run Commands

```bash
# Run the application (port 8089)
./mvnw spring-boot:run

# Build JAR (tests are skipped via pom.xml config)
./mvnw clean package

# Run tests explicitly
./mvnw test -DskipTests=false

# Compile only
./mvnw compile
```

## Prerequisites

- Java 17
- PostgreSQL database `sageLine_db` on localhost:5432 (user: postgres)
- External Python ML service on `http://localhost:5000` (optional — AIPredictionService falls back gracefully)

## Architecture

**Layered architecture:** Controller → Service → Repository → Entity

- **Package root:** `com.pfe.sageline`
- **Controllers** (`controller/`): REST endpoints under `/api/` — validations, lines, users, kpis, validation-results, validation-zones
- **Services** (`service/`): Business logic with `@Transactional`. Key services:
  - `AIPredictionService` — calls Python ML service at `/predict`, calculates deviations, falls back to defaults if unavailable
  - `KPIService` — conformity rate calculations, dashboard generation, auto-recalculates on validation closure
  - `ValidationService` — orchestrates validation lifecycle (start → add results → close), triggers AI predictions and KPI updates
- **Repositories** (`repository/`): Spring Data JPA with custom `@Query` methods (JPQL with LEFT JOIN FETCH for eager loading)
- **Entities** (`entity/`): JPA entities with Lombok annotations (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- **DTOs** (`dtos/`): Request/Response DTOs for all API contracts
- **Mappers** (`mappers/`): Manual entity ↔ DTO conversion classes
- **Exception** (`exception/`): `GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to structured error responses
- **Config** (`Config/`): `SecurityConfig` — currently permits all requests, has commented-out `@PreAuthorize` role-based access

## Data Model

Core entity relationships:
- **ProductionLine** → has many **ValidationZone**s and **KPI**s
- **ValidationZone** → has many **Validation**s
- **Validation** → has many **ValidationResult**s, has one **NonConformityPrediction**
- **User** → belongs to one **ProductionLine**, has a **Role** enum (ADMIN_IT, EXPERT, CHEF_SECTEUR, TECH_PREP, TECH_VAL, RESPONSABLE)
- **ValidationStatus** enum: EN_COURS, CONFORME, NON_CONFORME

## API Documentation

Swagger UI is available via SpringDoc OpenAPI at the default `/swagger-ui.html` endpoint when the app is running.

## Key Patterns

- All entities use Lombok — no manual getters/setters
- Services throw `ResourceNotFoundException` (404) and `ValidationException` (400)
- Repository queries use JPQL with `LEFT JOIN FETCH` to avoid N+1 problems
- `application.properties` has `spring.jpa.hibernate.ddl-auto=update` (auto-schema migration)
- SQL logging is enabled (`spring.jpa.show-sql=true`)
