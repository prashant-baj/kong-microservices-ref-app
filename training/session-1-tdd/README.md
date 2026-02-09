# Session 1: Test-Driven Development for Microservices

## Duration
2 hours

## Learning Objectives
By the end of this session, participants will be able to:
- Apply TDD as a design tool to shape service interfaces and API contracts
- Implement the Red-Green-Refactor cycle at service, controller, and integration layers
- Use MockMvc to test-drive REST APIs with proper status codes and error handling
- Write Testcontainers-based integration tests against real PostgreSQL
- Recognize when TDD adds value (business logic, contracts) vs. when it doesn't (config, boilerplate)
- Design idempotent operations through test-driven requirement discovery

## Target Audience
Experienced engineers (13+ years) pivoting to architecture roles. This is NOT an introduction to unit testing — we assume fluency with JUnit and Mockito. The focus is on TDD as an **architectural practice**.

## Session Flow

| Time | Module | Activity |
|------|--------|----------|
| 0:00 - 0:25 | Module 1: TDD at the Architecture Level | Presentation + Discussion |
| 0:25 - 1:00 | Module 2: Product Service TDD | Hands-on: Red-Green-Refactor |
| 1:00 - 1:35 | Module 3: Inventory Service TDD | Hands-on: Business logic + Testcontainers |
| 1:35 - 1:55 | Module 4: Order Service TDD | Hands-on: Inter-service mocking |
| 1:55 - 2:00 | Module 5: Wrap-Up | Bridge to Session 2 |

## Prerequisites
- Java 17+ installed
- Maven 3.9+ installed
- Docker Desktop running (for Testcontainers in Module 3)
- IDE with Java support (IntelliJ IDEA recommended)
- Clone the repository and verify: `mvn -version && docker info`
