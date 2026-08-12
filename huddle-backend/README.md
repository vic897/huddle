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

## Built so far
- `POST /api/auth/signup` — email/password + displayName, gated by `app.allowed-email-domains`
  (edit that list in `application.yml` to your real college domain before testing)
- `POST /api/auth/login` — returns a JWT
- `JwtAuthFilter` + `SecurityConfig` — all endpoints except `/api/health` and `/api/auth/**`
  now require a valid `Authorization: Bearer <token>` header
- `GET /api/users/me` / `PUT /api/users/me` — minimal profile surface (displayName, bio,
  githubUrl, skills, availability) — just enough to feed the matching algorithm, NOT full
  profile management (that's deferred to end-of-backend per the project plan)
- `GET /api/users/{id}` — view another user's profile (trimmed, no email/password exposed)
- `POST /api/posts` — create a project post (title, brief, full description, desired skills)
- `GET /api/posts` — plain browse, unranked. Only returns `PostSummaryResponse` (brief, never
  `fullDescription`) — this is where tiered visibility is actually enforced server-side
- `GET /api/posts/matched` — ranked browse using `MatchingService` (skill-complementarity +
  availability-overlap weighted scoring) against the current user's own profile

## Not built yet
- Matching algorithm (weighted skill + availability scoring)
- Matching-weight tuning based on real testing (current weights: 70% skill, 30% availability — arbitrary starting point)
- Request/accept/cooldown flow (`CollabRequest` entity + repository already exist, no endpoints yet)
- WebSocket chat (`ChatMessage` entity + repository already exist, no endpoints/config yet)
- Server-side re-validation of `CollabRequest.status == ACCEPTED` on every chat read/write —
  this is the specific access-control point the project plan calls out; don't skip it once
  chat endpoints exist.
- Full-scale profile management UX (deferred to end-of-backend, before frontend starts)
- Moderation (report/block)
