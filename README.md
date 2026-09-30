# Resource Booking System (RESTful API)

A production-grade RESTful Resource Booking System built with **Spring Boot 3**, **Java 17+**, **Spring Security**, **JWT (JSON Web Tokens)**, and **JPA/Hibernate**, supporting **MySQL**, **PostgreSQL**, and embedded **H2**.

---

## Table of Contents
1. [Key Features](#key-features)
2. [Tech Stack](#tech-stack)
3. [Architecture & Project Structure](#architecture--project-structure)
4. [Role-Based Access Control (RBAC) Matrix](#role-based-access-control-rbac-matrix)
5. [Pre-Seeded Test Credentials & Data](#pre-seeded-test-credentials--data)
6. [Quick Start & Setup Instructions](#quick-start--setup-instructions)
7. [Database Configuration & Profiles](#database-configuration--profiles)
8. [Environment Variables](#environment-variables)
9. [API Documentation (Swagger / OpenAPI & Postman)](#api-documentation-swagger--openapi--postman)
10. [API Endpoints Overview](#api-endpoints-overview)
11. [Running Tests](#running-tests)

---

## Key Features

- **JWT-Based Authentication**: Stateless authentication via `POST /auth/login` and `POST /auth/register` with BCrypt password hashing.
- **Strict User Identity via JWT**: User identity is extracted directly from the authenticated JWT token context, preventing impersonation or forged user IDs in reservation payloads.
- **Role-Based Access Control (RBAC)**: Distinct permissions for `ROLE_ADMIN` and `ROLE_USER`.
  - **ADMIN**: Full CRUD permissions on both Resources and Reservations; ability to view and manage all reservations across the system.
  - **USER**: Read-only access to Resources; permission to create reservations and view/cancel **only their own** reservations.
- **Resource Management**: Bookable items categorized into `ROOM`, `VEHICLE`, `EQUIPMENT`, or `OTHER`.
- **Reservation Lifecycle & Statuses**: Managed across `PENDING`, `CONFIRMED`, and `CANCELLED`.
- **High-Precision Decimal Pricing**: Reservation prices stored with `DECIMAL(12, 2)` precision using Java's `BigDecimal`.
- **Dynamic Query Filtering**: Filter reservations dynamically by `status`, `minPrice`, and `maxPrice`.
- **Pagination & Sorting**: Robust pagination via `page` and `size` parameters with customizable sorting (`sortBy` and `sortDir`).
- **Conflict Prevention**: Overlap detection prevents duplicate or overlapping bookings on the same resource.
- **Global Error Handling**: Standardized error responses with HTTP status codes, timestamp, path, error message, and granular field validation error maps.
- **Interactive Documentation**: Swagger UI at `/swagger-ui.html` and OpenAPI specification at `/v3/api-docs`.
- **Postman Collection**: Ready-to-import `postman_collection.json` with preconfigured environment variables and automated token capture.

---

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.3.1 |
| Language | Java 17+ (Tested on Java 21) |
| Security | Spring Security 6 (Stateless JWT Filter) |
| JWT Library | JJWT 0.12.6 |
| Persistence | Spring Data JPA / Hibernate ORM |
| Supported Databases | MySQL 8+, PostgreSQL 15+, H2 (in-memory dev) |
| API Docs | SpringDoc OpenAPI 2.6.0 (Swagger UI 3) |
| Build Tool | Apache Maven 3.9+ (includes `mvnw` wrapper) |
| Testing | JUnit 5, MockMvc, Spring Security Test |

---

## Architecture & Project Structure

The project adheres to clean layer separation:
```
com.exelynt.booking
│
├── config
│   ├── DataInitializer.java          # Seeds initial users, resources, and bookings
│   ├── OpenApiConfig.java            # Swagger OpenAPI 3 + JWT Security Scheme
│   └── SecurityConfig.java           # Spring Security filter chain, stateless session, RBAC
│
├── controller
│   ├── AuthController.java           # Authentication endpoints (/auth/login, /auth/register)
│   ├── ReservationController.java    # Reservation CRUD, filtering, pagination, sorting
│   └── ResourceController.java       # Resource management endpoints
│
├── dto
│   ├── AuthRequest.java
│   ├── AuthResponse.java
│   ├── ErrorResponse.java            # Standard JSON error response model
│   ├── PaginatedResponse.java        # Generic pagination wrapper
│   ├── RegisterRequest.java
│   ├── ReservationRequest.java       # Validated request (user identity derived from JWT)
│   ├── ReservationResponse.java
│   ├── ReservationStatusUpdateRequest.java
│   ├── ResourceRequest.java
│   └── ResourceResponse.java
│
├── entity
│   ├── Reservation.java              # Reservation entity with indexed columns
│   ├── ReservationStatus.java        # PENDING, CONFIRMED, CANCELLED
│   ├── Resource.java                 # Resource entity
│   ├── ResourceType.java             # ROOM, VEHICLE, EQUIPMENT, OTHER
│   ├── Role.java                     # ROLE_USER, ROLE_ADMIN
│   └── User.java                     # User entity
│
├── exception
│   ├── BadRequestException.java
│   ├── GlobalExceptionHandler.java   # @RestControllerAdvice for consistent HTTP status codes
│   ├── ReservationConflictException.java
│   └── ResourceNotFoundException.java
│
├── repository
│   ├── ReservationRepository.java    # JpaSpecificationExecutor + conflict query methods
│   ├── ReservationSpecification.java # Dynamic predicate builder (status, minPrice, maxPrice, user)
│   ├── ResourceRepository.java
│   └── UserRepository.java
│
├── security
│   ├── CustomUserDetailsService.java
│   ├── JwtAccessDeniedHandler.java   # 403 Forbidden handler
│   ├── JwtAuthenticationEntryPoint.java # 401 Unauthorized handler
│   ├── JwtAuthenticationFilter.java  # Bearer token validation filter
│   ├── JwtTokenProvider.java        # Token signing, parsing, expiration
│   └── UserPrincipal.java            # UserDetails implementation
│
└── ResourceBookingApplication.java   # Main entry point
```

---

## Role-Based Access Control (RBAC) Matrix

| Method | Endpoint | Accessible Roles | Description |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Obtain JWT Bearer token |
| `POST` | `/auth/register` | Public | Register a new user |
| `GET` | `/swagger-ui.html` | Public | Interactive Swagger UI |
| `GET` | `/v3/api-docs` | Public | OpenAPI JSON specification |
| `GET` | `/api/resources` | `USER`, `ADMIN` | List all resources (optional `?availableOnly=true`) |
| `GET` | `/api/resources/{id}` | `USER`, `ADMIN` | View specific resource details |
| `POST` | `/api/resources` | `ADMIN` only | Create a new bookable resource |
| `PUT` | `/api/resources/{id}` | `ADMIN` only | Update resource details |
| `DELETE` | `/api/resources/{id}` | `ADMIN` only | Delete a resource |
| `POST` | `/api/reservations` | `USER`, `ADMIN` | Create reservation (**User identity enforced from JWT**) |
| `GET` | `/api/reservations` | `USER`, `ADMIN` | List reservations with filtering/pagination. **USER sees only own; ADMIN sees all.** |
| `GET` | `/api/reservations/{id}` | `USER`, `ADMIN` | View reservation. **USER can view only own; ADMIN can view any.** |
| `PATCH` | `/api/reservations/{id}/status` | `USER`, `ADMIN` | **USER can only CANCEL own; ADMIN can update to any status.** |
| `PUT` | `/api/reservations/{id}` | `ADMIN` only | Full reservation modification |
| `DELETE` | `/api/reservations/{id}` | `ADMIN` only | Delete a reservation |

---

## Pre-Seeded Test Credentials & Data

When the application boots up, `DataInitializer` seeds test accounts and resources:

### Seed Users
| Username | Password | Role | Description |
|---|---|---|---|
| `admin` | `admin123` | `ROLE_ADMIN` | Administrator with full privileges |
| `user` | `user123` | `ROLE_USER` | Standard booking user |
| `john_doe` | `password123` | `ROLE_USER` | Second user for isolation testing |

### Seed Resources
1. **Executive Boardroom** (Type: `ROOM`, Base Price: `50.00`, Capacity: 16)
2. **Tesla Model 3** (Type: `VEHICLE`, Base Price: `75.00`, Capacity: 5)
3. **Sony FX6 Cinema Camera Kit** (Type: `EQUIPMENT`, Base Price: `120.00`, Capacity: 1)
4. **Grand Auditorium Hall** (Type: `ROOM`, Base Price: `250.00`, Capacity: 250)

---

## Quick Start & Setup Instructions

### Prerequisites
- **JDK 17+** (JDK 21 recommended)
- Maven 3.9+ (or use the included `./mvnw` / `mvnw.cmd`)

### 1. Run with Default In-Memory H2 (Instant Setup)
No database installation required:
```bash
# On Linux / macOS:
./mvnw clean spring-boot:run

# On Windows:
.\mvnw.cmd clean spring-boot:run
```
Once started:
- API Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:bookingdb`, Username: `sa`, Password: empty)

---

## Database Configuration & Profiles

The system supports switching between databases via Spring profiles or environment variables.

### A. Connecting to MySQL

1. Create a MySQL database:
```sql
CREATE DATABASE booking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Run with the `mysql` profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```
Or set environment variables:
```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/booking_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="your_password"
./mvnw spring-boot:run
```

### B. Connecting to PostgreSQL

1. Create a PostgreSQL database:
```sql
CREATE DATABASE booking_db;
```

2. Run with the `postgres` profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```
Or set environment variables:
```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/booking_db"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="your_password"
./mvnw spring-boot:run
```

---

## Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8080` | HTTP Server port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile (`dev`, `mysql`, `postgres`) |
| `JWT_SECRET` | *(required in non-dev)* | 256-bit secret key for signing JWTs (fail-fast validation) |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT validity duration in milliseconds |
| `APP_SEED_ENABLED` | `true` (dev) / `false` (prod) | Explicit opt-in flag for DataInitializer seeding |
| `CORS_ALLOWED_ORIGINS` | `*` | Allowed CORS origins (configured via CorsConfigurationSource) |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:bookingdb...` | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Database password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` (prod) / `update` (dev) | Hibernate schema update strategy |

---

## API Documentation (Swagger / OpenAPI & Postman)

### Interactive Swagger UI
Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```
- Click **Authorize** (lock icon) at the top right.
- Execute `POST /auth/login` with `{"username": "admin", "password": "admin123"}` or `user`.
- Copy the returned `token` and enter it into the Authorization modal (`Bearer <token>` or paste token directly).
- Test all protected endpoints interactively.

### Postman Collection
A complete Postman collection is included in the root directory: [`postman_collection.json`](postman_collection.json).
1. Open Postman.
2. Click **Import** and select `postman_collection.json`.
3. Running `Login as Admin` or `Login as User` automatically saves the JWT token into the collection variable, so all subsequent requests are authenticated automatically.

---

## API Endpoints Overview

### 1. Authentication
#### Login
`POST /auth/login`
```json
{
  "username": "user",
  "password": "user123"
}
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "username": "user",
  "role": "ROLE_USER",
  "expiresInMs": 86400000
}
```

### 2. Resources
- `GET /api/resources?availableOnly=true` (Accessible by `USER`, `ADMIN`)
- `POST /api/resources` (Accessible by `ADMIN` only)
```json
{
  "name": "Design Thinking Lab",
  "type": "ROOM",
  "description": "Collaborative workshop space",
  "location": "Innovation Hub, Room 101",
  "capacity": 25,
  "basePrice": 80.00,
  "available": true
}
```

### 3. Reservations
#### Create Reservation
`POST /api/reservations` (Header: `Authorization: Bearer <token>`)
*Notice: `userId` is NOT accepted in the request body; it is securely inferred from the JWT!*
```json
{
  "resourceId": 1,
  "startTime": "2026-10-15T09:00:00",
  "endTime": "2026-10-15T12:00:00",
  "price": 150.00,
  "notes": "Team workshop"
}
```

#### List Reservations with Filtering, Pagination, and Sorting
`GET /api/reservations?status=CONFIRMED&minPrice=50.00&maxPrice=500.00&page=0&size=10&sortBy=totalPrice&sortDir=desc`
- When called with **USER token**: Returns only reservations created by that user.
- When called with **ADMIN token**: Returns all reservations across all users matching filters.

#### Cancel Reservation
`PATCH /api/reservations/{id}/status`
```json
{
  "status": "CANCELLED"
}
```

---

## Running Tests

The test suite covers unit and integration tests across authentication, authorization, RBAC constraints, resource CRUD, reservation ownership, conflict detection, price validation, filtering, pagination, and sorting:

```bash
# On Linux / macOS:
./mvnw clean test

# On Windows:
.\mvnw.cmd clean test
```

### Test Suite Summary:
- **`AuthControllerIntegrationTest`**: Login flow, token generation, invalid credentials, user registration, duplicate username handling.
- **`ResourceControllerIntegrationTest`**: Unauthenticated 401 check, USER read-only enforcement, USER 403 Forbidden on create/update/delete, ADMIN full CRUD.
- **`ReservationControllerIntegrationTest`**: JWT identity extraction, overlapping booking conflict (409 Conflict), start/end time validation, USER ownership isolation, non-admin price override rejection, invalid sortBy parameter rejection, ADMIN global visibility, status/price filtering, pagination, and sorting.
- **`ResourceBookingApplicationTests`**: Spring context bootstrap and configuration verification.
- **Total: 33 tests passing with 0 failures.**
