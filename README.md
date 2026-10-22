# Huddle

Huddle is a student-focused collaboration platform where students can discover project ideas, find teammates with complementary skills, send collaboration requests, discuss potential collaboration through real-time chat, and form project teams. It is built as a full-stack web application with a Spring Boot backend, a Next.js frontend, and a PostgreSQL database.

---

## Overview

**What it is:** A project team-finding and collaboration platform for students.

**What problem it solves:** Students working on academic or side projects often struggle to find teammates whose skills complement their own. Huddle provides a structured way to post project ideas, discover projects that match your skill set, request collaboration, and communicate with potential teammates — all in one place.

**Who it's for:** College/university students looking for project collaborators.

**How it works:** Students sign up with their college email, build a profile listing their skills and availability, browse or search for projects, and send collaboration requests. Project owners review incoming requests and accept or reject them. Once a request is accepted, both parties can communicate via real-time chat. A weighted matching algorithm ranks projects by how well they align with the student's skills and availability.

---

## Core Concept

```
Student signs up with college email
        ↓
Builds profile (skills, availability, bio)
        ↓
Posts a project  —or—  Browses existing projects
        ↓
Discovers a project that needs their skills
        ↓
Sends a collaboration request with a message
        ↓
Project owner reviews and accepts/rejects
        ↓
If accepted → real-time chat channel opens
        ↓
Team formation begins
```

---

## Current Features

### Authentication & College Verification

Email/password authentication with JWT tokens. Signup is gated by a configurable list of allowed email domains (currently `apsit.edu.in`), ensuring only students from approved institutions can register. Passwords are hashed with BCrypt. JWT tokens are issued on login/signup with a 24-hour expiration.

### Student Profiles

Users have profiles with display name, bio, GitHub URL, skills (as a list), and availability (e.g., "weekdays", "weekends", "flexible"). Profiles can be viewed and edited. Other users can view a public version of any profile (without email or password exposure).

### Project Posts

Students can create project posts with a title, brief description (280 characters max), full description, desired skills, maximum team size, and recruiting status. The browse view shows only the brief summary — the full description is revealed only on the post detail page, enforcing tiered visibility server-side.

### Project Discovery & Search

Students can browse all currently recruiting projects, search projects by desired skill, and view full project details. The browse page supports toggling between "All Projects" and "Matched For You" views.

### Matching Algorithm

An explainable weighted matching algorithm ranks projects against the current user's profile:

| Factor                  | Weight | How It Works                                                                                          |
|-------------------------|--------|-------------------------------------------------------------------------------------------------------|
| Skill Complementarity   | 70%    | Counts how many of the project's desired skills match the user's skills. Score = matches / total desired skills. |
| Availability Overlap    | 30%    | Direct string comparison of user's availability vs. project owner's availability. 1.0 if equal, 0.0 otherwise. |

The total match score is displayed as a percentage on matched project cards. Posts from blocked users and the user's own posts are excluded.

> **Note:** Availability matching currently uses exact string equality (e.g., "weekdays" == "weekdays"). There is no partial or fuzzy overlap logic. The 70/30 weights are a starting point and have not been tuned against real user data.

### Collaboration Requests

Students can send a collaboration request to a project with a message/pitch. The system prevents duplicate requests and self-requests. Project owners can accept, reject, or view incoming requests. Requesters can cancel pending requests. Each status change triggers a notification.

**Request lifecycle:**
```
PENDING  →  ACCEPTED  (by owner)
PENDING  →  REJECTED  (by owner)
PENDING  →  CANCELLED (by requester)
```

### Real-Time Chat

Once a collaboration request is accepted, both the requester and the project owner gain access to a real-time chat channel tied to that specific request.

- **Technology:** WebSocket with STOMP protocol (Spring WebSocket on backend, `@stomp/stompjs` on frontend, SockJS fallback)
- **Access control:** The backend validates that the sender is a participant in the collaboration request and that the request status is `ACCEPTED` before allowing messages
- **Persistence:** All messages are stored in the database and can be retrieved via REST API (chat history)
- **UI:** Split-panel messages page with a conversation list (left) and chat window (right), message bubbles styled by sender, real-time delivery without page refresh

### Notifications

A persistent notification system tracks key events:

| Notification Type    | Triggered When                         |
|----------------------|----------------------------------------|
| `COLLAB_REQUEST`     | Someone sends a collaboration request  |
| `REQUEST_ACCEPTED`   | A request is accepted by the owner     |
| `REQUEST_REJECTED`   | A request is rejected                  |
| `NEW_MESSAGE`        | A new chat message is received         |

Users can view all notifications, see unread count (displayed as a badge on the navbar bell icon), mark individual notifications as read, or mark all as read.

### User Blocking

Users can block other users. Blocked users' posts are excluded from browse and search results, and blocked users are excluded from user search results. Block/unblock and listing blocked users are all supported.

### User Search

The backend supports searching users by skill (`GET /api/users/search?skill=X`), with results filtering out blocked users. The frontend API client includes this method, though there is no dedicated people-search page — user search is available as an API.

---

## Current Application Flow

```
1. Land on public landing page (/)
           ↓
2. Sign up with college email (/auth/signup)
   — Email domain validated against allowed list
   — JWT token stored in localStorage
           ↓
3. Arrive at Home dashboard (/home)
   — See welcome message, quick actions
   — View own projects and top matched projects
           ↓
4. Browse projects (/browse)
   — Toggle "All Projects" or "Matched For You"
   — Search by skill
   — Click into project detail
           ↓
5. View project detail (/post/{id})
   — Read full description
   — Send collaboration request with a message
           ↓
6. Manage requests (/requests)
   — "Received" tab: accept/reject incoming requests
   — "Sent" tab: view/cancel outgoing requests
           ↓
7. Chat with accepted collaborators (/messages)
   — Select conversation from list
   — Real-time messaging via WebSocket
           ↓
8. Check notifications (/notifications)
   — View and manage all activity notifications
           ↓
9. Edit profile (/profile)
   — Update skills, availability, bio, GitHub URL
```

---

## Authentication & Authorization

### Current Implementation

| Aspect                | Detail                                                              |
|-----------------------|---------------------------------------------------------------------|
| Auth mechanism        | JWT (JSON Web Tokens) via `jjwt` library (v0.12.5)                  |
| Password storage      | BCrypt hash                                                         |
| Token expiration      | 24 hours                                                            |
| Token transport       | `Authorization: Bearer <token>` header                              |
| College verification  | Signup email domain checked against `app.allowed-email-domains` list |
| Frontend persistence  | `localStorage` (token, userId, email, displayName)                  |
| Session management    | Stateless (no server-side sessions)                                 |
| Role model            | Single role: `ROLE_USER` (all authenticated users)                  |

### Protected Endpoints

All endpoints require authentication except:
- `GET /api/health`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `/ws/**` (WebSocket endpoint — authentication handled at message level)

### Chat Authorization

The `ChatController` validates on every message send that:
1. The sender is authenticated
2. The sender is either the requester or the project owner for the given collaboration request
3. The collaboration request status is `ACCEPTED`

Chat history retrieval performs the same participant check.

---

## Real-Time Communication

| Component         | Technology                                            |
|-------------------|-------------------------------------------------------|
| Protocol          | WebSocket with STOMP sub-protocol                     |
| Backend           | Spring WebSocket (`spring-boot-starter-websocket`)    |
| Frontend          | `@stomp/stompjs` ^7.3.0                               |
| Fallback          | SockJS                                                |
| Endpoint          | `ws://localhost:8080/ws`                               |
| Message routing   | `/app/chat.send` → `/topic/chat.{requestId}`          |
| Persistence       | Messages saved to `chat_message` table via JPA        |
| History           | `GET /api/chat/{requestId}/history` (REST)            |

Messages are scoped to individual collaboration requests. Each accepted request has its own STOMP topic. The backend sends a notification (`NEW_MESSAGE`) to the recipient when a message is received.

---

## Notifications

Notifications are stored in the database and served via REST API. There is **no real-time push** for notifications — the frontend fetches the unread count on page load (in the Navbar component). Users must refresh or navigate to see new notifications.

| Endpoint                            | Purpose                     |
|-------------------------------------|-----------------------------|
| `GET /api/notifications`            | All notifications           |
| `GET /api/notifications/unread`     | Unread only                 |
| `GET /api/notifications/unread/count` | Unread count (for badge)  |
| `PUT /api/notifications/{id}/read`  | Mark one as read            |
| `PUT /api/notifications/read-all`   | Mark all as read            |

---

## Project / Collaboration System

### Current Implementation

The current system follows a straightforward request → accept/reject model:

```
Student discovers project
        ↓
Sends collaboration request (with message)
        ↓
Project owner reviews
        ↓
    ┌────────┬──────────┐
 Accept    Reject    (Requester cancels)
    ↓        ↓            ↓
 ACCEPTED  REJECTED   CANCELLED
    ↓
 Chat channel opens
```

**Currently, accepting a request immediately grants chat access.** There is no intermediate commitment discussion step.

### Planned: Collaboration Commitment Layer

A future architectural change intends to add a commitment phase between acceptance and full project access:

```
Project Discovery
       ↓
Collaboration Request
       ↓
Owner Accepts
       ↓
Commitment Discussion (new phase)
       ↓
Participant Decision
       ↓
  ┌──────────────┐
  │              │
Commit         Leave
  │              │
  ↓              ↓
Committed      Closed
Member
  ↓
Private Project Access
```

The key security principle: **accepting a request should not automatically grant access to private project resources.** Only an actively committed member should receive protected access. This is not yet implemented.

---

## Technology Stack

### Frontend

| Technology      | Version / Detail                       |
|-----------------|----------------------------------------|
| Framework       | Next.js 16.3.3 (App Router)           |
| Language        | TypeScript 5                           |
| UI Library      | React 19.2.8                           |
| Styling         | Tailwind CSS v4                        |
| Icons           | lucide-react ^1.37.0                   |
| WebSocket       | @stomp/stompjs ^7.3.0                  |
| State Mgmt      | React hooks (useState, useEffect)      |
| Auth Persistence| localStorage                           |
| Build Tool      | Next.js built-in (Turbopack)           |

### Backend

| Technology      | Version / Detail                       |
|-----------------|----------------------------------------|
| Language        | Java 17                                |
| Framework       | Spring Boot 3.3.2                      |
| ORM             | Spring Data JPA / Hibernate            |
| Auth            | Spring Security + jjwt 0.12.5          |
| WebSocket       | Spring WebSocket + spring-security-messaging |
| Validation      | spring-boot-starter-validation         |
| Build Tool      | Maven                                  |
| Utilities       | Lombok                                 |

### Database

| Technology      | Detail                                 |
|-----------------|----------------------------------------|
| Database        | PostgreSQL                             |
| ORM             | Hibernate (via Spring Data JPA)        |
| Schema Mgmt     | `hibernate.ddl-auto: update` (auto-create/alter) |
| Database Name   | `collabfinder`                         |

### Infrastructure

No deployment configuration, CI/CD pipelines, Docker files, or cloud infrastructure is present in the repository. The application runs locally in development mode.

---

## Project Structure

```
huddlee/
├── huddle-backend/                # Spring Boot backend
│   ├── pom.xml                    # Maven config, dependencies
│   ├── src/main/java/com/huddle/backend/
│   │   ├── BackendApplication.java
│   │   ├── config/
│   │   │   ├── CorsConfig.java         # CORS settings
│   │   │   ├── SecurityConfig.java     # Spring Security config
│   │   │   └── WebSocketConfig.java    # STOMP/WebSocket config
│   │   ├── controller/
│   │   │   ├── ChatController.java     # WebSocket chat + history
│   │   │   ├── CollabRequestController.java
│   │   │   ├── HealthController.java
│   │   │   ├── MatchingController.java
│   │   │   ├── NotificationController.java
│   │   │   ├── PostController.java
│   │   │   └── UserBlockController.java
│   │   │   └── UserController.java
│   │   ├── dto/                   # Request/response DTOs
│   │   ├── model/                 # JPA entities
│   │   │   ├── ChatMessage.java
│   │   │   ├── CollabRequest.java
│   │   │   ├── Notification.java
│   │   │   ├── ProjectPost.java
│   │   │   ├── User.java
│   │   │   └── UserBlock.java
│   │   ├── repository/            # Spring Data repositories
│   │   ├── security/
│   │   │   ├── AuthController.java     # Signup/login endpoints
│   │   │   ├── JwtAuthFilter.java      # JWT filter
│   │   │   └── JwtUtil.java            # JWT generation/validation
│   │   └── service/
│   │       ├── MatchingService.java    # 70/30 matching algorithm
│   │       └── NotificationService.java
│   └── src/main/resources/
│       └── application.yml        # App configuration
│
├── huddle-frontend/               # Next.js frontend
│   ├── package.json
│   ├── next.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── app/
│       │   ├── globals.css             # Theme & global styles
│       │   ├── layout.tsx              # Root layout
│       │   ├── page.tsx                # Landing page
│       │   ├── auth/login/page.tsx
│       │   ├── auth/signup/page.tsx
│       │   ├── home/page.tsx           # Dashboard
│       │   ├── browse/page.tsx         # Project discovery
│       │   ├── post/[id]/page.tsx      # Project detail
│       │   ├── create-post/page.tsx
│       │   ├── requests/page.tsx       # Sent/received requests
│       │   ├── messages/page.tsx       # Real-time chat
│       │   ├── notifications/page.tsx
│       │   └── profile/page.tsx
│       ├── components/
│       │   ├── AppShell.tsx            # Auth-guarded layout wrapper
│       │   ├── Navbar.tsx              # Top nav with notif badge
│       │   └── Sidebar.tsx             # Left navigation
│       └── lib/
│           └── api.ts                  # API client
│
├── design-references/             # Static HTML design prototypes
│   ├── landing.html
│   ├── home.html
│   ├── browse.html
│   ├── profile_page.html
│   ├── matched_hub.html
│   ├── matched_people.html
│   └── screenshots/
│
└── mods/                          # Planning/discussion documents
    └── New Text Document.txt      # UI modification plans
```

---

## API Overview

### Authentication
| Method | Endpoint                | Description          |
|--------|-------------------------|----------------------|
| POST   | `/api/auth/signup`      | Register new user    |
| POST   | `/api/auth/login`       | Login, receive JWT   |

### Users
| Method | Endpoint                | Description               |
|--------|-------------------------|---------------------------|
| GET    | `/api/users/me`         | Current user's profile    |
| PUT    | `/api/users/me`         | Update own profile        |
| GET    | `/api/users/{id}`       | Public profile of a user  |
| GET    | `/api/users/search`     | Search users by skill     |

### Projects
| Method | Endpoint                | Description                          |
|--------|-------------------------|--------------------------------------|
| POST   | `/api/posts`            | Create a project post                |
| GET    | `/api/posts`            | List all recruiting posts (brief only)|
| GET    | `/api/posts/{id}`       | Full project detail                  |
| GET    | `/api/posts/mine`       | Current user's posts                 |
| GET    | `/api/posts/matched`    | Ranked posts by matching algorithm   |
| GET    | `/api/posts/search`     | Search posts by desired skill        |

### Collaboration Requests
| Method | Endpoint                      | Description                |
|--------|-------------------------------|----------------------------|
| POST   | `/api/requests`               | Send collaboration request |
| GET    | `/api/requests/sent`          | Requests sent by user      |
| GET    | `/api/requests/received`      | Requests received by user  |
| PUT    | `/api/requests/{id}/accept`   | Accept a request           |
| PUT    | `/api/requests/{id}/reject`   | Reject a request           |
| PUT    | `/api/requests/{id}/cancel`   | Cancel a request           |

### Chat
| Method | Endpoint                       | Description         |
|--------|--------------------------------|---------------------|
| GET    | `/api/chat/{requestId}/history`| Chat message history|
| STOMP  | `/app/chat.send`               | Send chat message   |

### Notifications
| Method | Endpoint                          | Description            |
|--------|-----------------------------------|------------------------|
| GET    | `/api/notifications`              | All notifications      |
| GET    | `/api/notifications/unread`       | Unread notifications   |
| GET    | `/api/notifications/unread/count` | Unread count           |
| PUT    | `/api/notifications/{id}/read`    | Mark as read           |
| PUT    | `/api/notifications/read-all`     | Mark all as read       |

### Blocking
| Method | Endpoint               | Description       |
|--------|------------------------|--------------------|
| POST   | `/api/blocks/{userId}` | Block a user       |
| DELETE | `/api/blocks/{userId}` | Unblock a user     |
| GET    | `/api/blocks`          | List blocked users |

### Health
| Method | Endpoint       | Description    |
|--------|----------------|----------------|
| GET    | `/api/health`  | Health check   |

---

## Database Overview

### Entities

| Entity          | Key Fields                                                                                   |
|-----------------|----------------------------------------------------------------------------------------------|
| **User**        | id, email (unique), passwordHash, displayName, bio, githubUrl, skills[], availability        |
| **ProjectPost** | id, owner (→User), title, brief, fullDescription, desiredSkills[], maxTeamSize, recruiting   |
| **CollabRequest** | id, requester (→User), post (→ProjectPost), message, status, createdAt, updatedAt          |
| **ChatMessage** | id, request (→CollabRequest), sender (→User), body, sentAt                                  |
| **Notification**| id, recipient (→User), type, referenceId, message, read, createdAt                          |
| **UserBlock**   | id, blocker (→User), blocked (→User), createdAt — unique constraint on (blocker, blocked)   |

### Relationships

```
User ──< ProjectPost        (one user owns many posts)
User ──< CollabRequest       (one user sends many requests)
ProjectPost ──< CollabRequest (one post receives many requests)
CollabRequest ──< ChatMessage (one accepted request has many messages)
User ──< ChatMessage         (one user sends many messages)
User ──< Notification        (one user receives many notifications)
User ──< UserBlock           (one user blocks many users)
```

---

## UI & Design

### Design System

The frontend uses a **dark-theme-only** design with a custom CSS variable system defined in `globals.css`:

- **Background:** Deep navy/black (`#0a0a0f` primary, `#12121a` secondary, `#16161f` cards)
- **Text:** Light gray/white (`#e8e8f0` primary, `#a0a0b8` secondary)
- **Accent:** Purple-to-blue gradient (`#7c5cfc` primary purple, `#5c8cfc` secondary blue)
- **Semantic colors:** Green (success), yellow (warning), red (danger), blue (info)
- **Border radius:** 6px–24px scale
- **Shadows:** Dark-mode-appropriate with high opacity

### Layout

- **AppShell pattern:** All authenticated pages use a shared `AppShell` wrapper with:
  - Fixed left sidebar (navigation links with icons)
  - Top navbar (brand, search input, notification bell, user menu)
  - Scrollable main content area
- **Landing page:** Full-screen standalone page (no AppShell) with animated gradient orbs

### Typography

System font stack (`antialiased`). Geist font is imported but not actively applied to the body.

### Responsive Behavior

- Sidebar collapses into a hamburger-toggled overlay on mobile
- Grids adapt from 3 columns → 2 → 1 across breakpoints
- Messages page switches from split-panel to stacked view on mobile
- All forms and cards are responsive

### Key Pages

| Page           | Description                                                    |
|----------------|----------------------------------------------------------------|
| Landing (`/`)  | Hero section, feature highlights, stats, CTA buttons           |
| Home           | Dashboard with welcome, quick actions, own projects, matches   |
| Browse         | Project grid with search and all/matched toggle                |
| Post Detail    | Full project info with collaboration request form              |
| Create Post    | Project creation form with validation                          |
| Requests       | Tabbed view (received/sent) with status badges and actions     |
| Messages       | Split-panel real-time chat with conversation list              |
| Notifications  | Chronological notification list with read/unread state         |
| Profile        | View/edit profile with skill badges and availability           |
| Login/Signup   | Centered card forms with purple accents                        |

### No Light Mode

There is no light theme or theme toggle. The application is dark-mode-only.

---

## Security

### Currently Implemented

| Mechanism                | Detail                                                                 |
|--------------------------|------------------------------------------------------------------------|
| Password hashing         | BCrypt via Spring Security                                              |
| JWT authentication       | HMAC-SHA signed tokens, 24h expiry                                      |
| Email domain gating      | Signup restricted to `app.allowed-email-domains` list                   |
| Endpoint protection      | All endpoints except auth and health require valid JWT                  |
| Chat access control      | Server validates participant identity and ACCEPTED status on every message |
| Tiered content visibility| Browse shows brief only; full description requires specific post request |
| Block enforcement         | Blocked users filtered from search and browse results server-side       |
| CORS                     | Restricted to `localhost:3000` and `localhost:3001`                      |
| CSRF                     | Disabled (stateless JWT architecture)                                   |
| Input validation         | Spring Validation annotations on request DTOs                           |
| Duplicate prevention     | Server prevents duplicate collaboration requests                        |

### Security Considerations

- **JWT secret:** Defaults to `default-dev-secret-change-in-prod` if `JWT_SECRET` environment variable is not set. This must be changed for any non-local deployment.
- **localStorage tokens:** JWT is stored in `localStorage` on the frontend, which is vulnerable to XSS. HttpOnly cookies would be more secure but are not currently used.
- **WebSocket authentication:** The `/ws` endpoint is permitted without JWT in the HTTP security config. Authentication is enforced at the STOMP message handling level in `ChatController`, not at the WebSocket connection level.
- **No rate limiting:** There is no rate limiting on API endpoints including authentication.
- **No HTTPS:** The application runs on plain HTTP in development. HTTPS would be required for production.
- **Database credentials:** Default to `postgres`/`postgres` if environment variables are not set.

---

## Current Status

### Implemented
- JWT authentication with BCrypt password hashing
- Email domain-based college verification (configurable allowed domains)
- User profiles with skills and availability
- Project posts with CRUD, tiered visibility, and recruiting status
- Weighted matching algorithm (70% skill complementarity / 30% availability overlap)
- Collaboration request lifecycle (create, accept, reject, cancel)
- Real-time chat via WebSocket/STOMP (scoped to accepted requests)
- Chat access control (participant + status validation)
- Persistent notification system with unread tracking
- User blocking with server-side enforcement
- Full frontend with dashboard, browse, chat, notifications, profile, requests
- Dark-theme design system with responsive layout
- User and project search by skill

### Not Yet Implemented
- Collaboration commitment layer (accept → commit → access)
- Google/OAuth authentication
- Institutional domain verification (beyond hardcoded domain list)
- Light theme or theme toggle
- Real-time notification push (currently fetch-on-load only)
- Moderation tools (beyond basic blocking)
- Automated seed/bot data generation
- Production deployment configuration
- Automated tests
- People search UI page (backend API exists but no dedicated frontend page)

---

## Future Roadmap

> The following items are planned or discussed but **not yet implemented**.

1. **Collaboration Commitment Layer** — Add a commitment discussion phase between request acceptance and full project access, so that accepting a request does not automatically grant access to private project resources.

2. **Google Authentication** — Replace email/password auth with Google Sign-In. Use Google's `hd` (hosted domain) claim to determine institutional affiliation rather than relying on hardcoded email domain suffixes.

3. **Institution/Domain Verification** — Build a system to verify whether a Google Workspace domain belongs to an educational institution, rather than depending on domain suffixes like `.edu` or `.ac.in`.

4. **Home / Browse Separation** — Redesign Home as a personalized dashboard (activity feed, quick actions, workspace overview) distinct from Browse as a dedicated project discovery/search experience. Currently planned in `mods/` documentation.

5. **Public Deployment** — Deploy Huddle as a publicly accessible platform supporting multiple colleges and universities.

6. **Moderation** — Reporting, content moderation, and administrative tools beyond basic user blocking.

7. **Production Hardening** — HTTPS, secure cookie-based tokens, rate limiting, proper secret management, monitoring.

8. **Improved Matching** — Tune 70/30 weights based on real data, add fuzzy availability matching, possibly incorporate additional signals.

9. **Automated Seed/Bot Data** — Create a data seeding mechanism to populate development and demo environments with realistic users, projects, requests, and messages.

10. **Real-Time Notifications** — Push notifications via WebSocket instead of fetch-on-load polling.

---

## Running Locally

### Prerequisites

- **Java 17** (JDK)
- **Maven** (or use `mvn` if globally installed)
- **Node.js** (with npm)
- **PostgreSQL** running locally

### Database Setup

Create the PostgreSQL database:

```bash
createdb collabfinder
```

### Environment Variables

Set the following before running the backend:

```
DB_USERNAME=<your-postgres-username>    # defaults to "postgres" if not set
DB_PASSWORD=<your-postgres-password>    # defaults to "postgres" if not set
JWT_SECRET=<a-strong-secret-key>       # defaults to "default-dev-secret-change-in-prod"
```

Optionally update `app.allowed-email-domains` in `huddle-backend/src/main/resources/application.yml` to match your institution's email domain.

### Backend

```bash
cd huddle-backend
mvn spring-boot:run
```

The backend starts on **http://localhost:8080**.

Verify with:
```bash
curl http://localhost:8080/api/health
# → {"status":"ok","service":"huddle-backend"}
```

### Frontend

```bash
cd huddle-frontend
npm install
npm run dev
```

The frontend starts on **http://localhost:3000**.

---

## Environment Variables

| Variable       | Used By  | Description                                  | Default                          |
|----------------|----------|----------------------------------------------|----------------------------------|
| `DB_USERNAME`  | Backend  | PostgreSQL username                           | `postgres`                       |
| `DB_PASSWORD`  | Backend  | PostgreSQL password                           | `postgres`                       |
| `JWT_SECRET`   | Backend  | Secret key for signing JWT tokens             | `default-dev-secret-change-in-prod` |

The allowed email domains for signup are configured in `application.yml` under `app.allowed-email-domains`, not via environment variable.

---

## Development Notes

- **Schema management:** Hibernate `ddl-auto` is set to `update`, meaning the database schema is auto-created and altered based on JPA entities. This is convenient for development but should not be used in production.
- **No tests:** The `src/test` directory exists but contains no test files. There are no unit or integration tests.
- **Design references:** The `design-references/` directory contains static HTML prototypes of earlier design iterations (landing, home, browse, profile, matched pages) with a capture script for screenshots. These are reference mockups, not the live application.
- **CORS:** Configured for `localhost:3000` and `localhost:3001` only. Must be updated for any deployment.
- **WebSocket origins:** Also restricted to localhost ports. Must be updated for deployment.
- **Frontend state:** No state management library is used. Authentication state is stored in `localStorage` and component state uses React hooks. There is no global state store.
- **Navbar search:** The search input in the top navbar is currently decorative and does not trigger any search action. Actual search is available on the Browse page.
