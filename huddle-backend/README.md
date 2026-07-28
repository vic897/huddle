# Huddle — Backend (Sem 5)

Spring Boot skeleton for the student teammate-matching platform. Matches the plan in
`CollabFinder_Project_Plan.pdf`.

## Prerequisites
- JDK 17
- Maven (or use the wrapper once generated — `mvn -N io.takari:maven:wrapper` if you want `./mvnw`)
- PostgreSQL running locally, with a `collabfinder` database created:
  ```
  createdb collabfinder
  ```

## Configuration
Set these as environment variables (don't hardcode in `application.yml`, especially before
any deploy):
- `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`

Also update `app.allowed-email-domains` in `application.yml` to your actual college domain.

## Run
```
mvn spring-boot:run
```
Then check:
```
curl http://localhost:8080/api/health
```
Expect `{"status":"ok","service":"huddle-backend"}`.

## What's here
- `model/` — JPA entities: `User`, `ProjectPost`, `CollabRequest`, `ChatMessage`
- `repository/` — Spring Data repositories for each entity
- `config/SecurityConfig` — **permissive placeholder**, everything is open right now so the
  app boots. This is the first thing to replace once auth work starts (Week 1-2 of the plan).
- `controller/HealthController` — sanity-check endpoint

## Not built yet (by design — this is the skeleton, not Week 1-2 output)
- Signup/login endpoints + JWT issuing/validation
- College-domain email verification logic
- Post CRUD + browse/matching endpoints
- Matching algorithm (weighted skill + availability scoring)
- Request/accept/cooldown flow
- WebSocket chat config + endpoints
- Server-side re-validation of `CollabRequest.status == ACCEPTED` on every chat read/write —
  this is the specific access-control point the project plan calls out; don't skip it once
  chat endpoints exist.
