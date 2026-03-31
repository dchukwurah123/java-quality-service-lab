# java-quality-service-lab

Interview-quality Java 21 + Spring Boot service implementing a generic approval workflow with realistic business rules, clean structure, and strong automated tests.

## Stack

- Java 21
- Spring Boot 3
- Maven
- Spring Web + Validation + Data JPA
- PostgreSQL (runtime) + H2 (default local datasource)
- JUnit 5 + Mockito + Spring Boot Test
- Testcontainers (PostgreSQL) for integration tests
- JaCoCo coverage checks
- GitHub Actions CI

## Architecture

Layered, package-by-feature design to keep business rules explicit and testable:

- `approval.api`: REST controllers + request/response DTOs
- `approval.application`: use-case service + command objects + app exceptions
- `approval.domain`: entity, status enum, transition rules
- `approval.infrastructure`: Spring Data repository
- `shared.api`: global error handling + common error response

### Why this structure

- Keeps HTTP concerns out of domain logic.
- Keeps transition rules centralized in one entity model.
- Makes service-layer tests focused and fast.
- Stays readable for interviews without introducing complex frameworks.

## Approval workflow rules

- New requests start as `DRAFT`.
- Only `DRAFT` and `RETURNED` requests can be edited.
- `DRAFT` can be `SUBMITTED`.
- Only `SUBMITTED` requests can be `APPROVED` or `RETURNED`.
- `RETURNED` requests can be resubmitted.
- `APPROVED` requests are immutable.
- Return action requires a non-blank comment.
- Approve and return actions create audit entries.

## API

Base path: `/api/v1/approvals`

- `POST /` create approval request
- `PUT /{id}` update request while editable
- `GET /{id}` fetch by id
- `GET /?status=&requestedBy=&approver=` list/filter
- `POST /{id}/submit`
- `POST /{id}/approve`
- `POST /{id}/return`
- `GET /{id}/audits`

## Error handling

All errors use a consistent JSON shape:

- `timestamp`
- `status`
- `error`
- `message`
- `path`
- `validationErrors` (only for validation failures)

Status mapping:

- `400` validation / malformed request
- `404` unknown approval request
- `409` invalid business transition
- `500` unexpected server error

## Test strategy

### Unit tests (service layer)

`ApprovalServiceTest` validates core business behavior quickly using mocked repositories:

- create sets `DRAFT`
- update restrictions by state
- submit, return, resubmit, approve transitions
- return comment required
- audit entry creation for approve/return
- not-found handling

### Integration tests

`ApprovalControllerIntegrationTest` uses Spring Boot + MockMvc + Testcontainers PostgreSQL:

- create/get/list flow
- transition flow with audits
- validation and not-found error contracts

> Integration tests are marked with `@Testcontainers(disabledWithoutDocker = true)`, so they skip when Docker is unavailable.

## Coverage

JaCoCo runs on `mvn verify` and enforces:

- line coverage >= 80%
- branch coverage >= 70%

HTML report:

- `target/site/jacoco/index.html`

## Local run

1. Install Java 21 and Maven 3.9+.
2. Start app:

```bash
mvn spring-boot:run
```

3. Example create call:

```bash
curl -X POST http://localhost:8080/api/v1/approvals \
  -H "Content-Type: application/json" \
  -d "{\"subject\":\"Laptop Purchase\",\"description\":\"Need replacement device\",\"requestedBy\":\"alice\",\"approver\":\"manager\"}"
```

4. Submit draft:

```bash
curl -X POST http://localhost:8080/api/v1/approvals/{id}/submit \
  -H "Content-Type: application/json" \
  -d "{\"actor\":\"alice\"}"
```

5. List requests:

```bash
curl "http://localhost:8080/api/v1/approvals?status=DRAFT"
```

## Local test commands

- Unit + integration tests: `mvn test`
- Full verification + coverage gate: `mvn verify`

## Docker

Build image:

```bash
docker build -t java-quality-service-lab:local .
```

Run container:

```bash
docker run --rm -p 8080:8080 java-quality-service-lab:local
```

## CI

GitHub Actions workflow: `.github/workflows/ci.yml`

- Runs on push to `main` and all pull requests
- Executes `mvn verify` on Java 21
