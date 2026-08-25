# E-Commerce Application

This is a full-stack e-commerce application built with Clojure (backend) and ClojureScript (frontend).

**Code Challenge:** [Code Challenge.pdf](docs/Code%20Challenge.pdf)

**CSV file downloaded:** August 18, 2026

**PowerPoint Presentation:** [Executive Summary](docs/E-Commerce%20Code%20Challenge.pptx)

**Live Web site:** [https://ecommerce.appliedprogramming.io](https://ecommerce.appliedprogramming.io)

**Note from the Author**


> I used AI Claude Code, guided by my experience shipping enterprise-grade production systems. The decisions below reflect what I'd push for on a real project: security analysis of the CSV file, infrastructure as code, CI/CD, HTTPS, and auth. The code challenge didn't ask for it, but it was a conscious decision to show the end-to-end capabilities and the kind of projects I would like to engage in.

Thanks for considering me as a candidate.

Adrian Perez

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    Browser                          │
│         ClojureScript SPA (Reagent/Re-frame)        │
│         Client-side routing (Reitit)                │
│         Tailwind CSS                                │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS
              ┌────────▼────────┐
              │  Route 53 DNS   │
              │  ACM (TLS cert) │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   ALB (HTTPS)   │
              │   Multi-AZ      │
              └────────┬────────┘
                       │ HTTP :80
┌──────────────────────▼──────────────────────────────┐
│              EC2 (Docker Compose)                    │
│  ┌────────────────────────────────────────────────┐ │
│  │            Clojure Backend                     │ │
│  │  Ring + Reitit │ Auth (JWT)                    │ │
│  │  Handlers → Services → DB Layer (next.jdbc)    │ │
│  └──────────────────────┬─────────────────────────┘ │
│                         │                            │
│  ┌──────────────────────▼─────────────────────────┐ │
│  │          PostgreSQL 16 (HikariCP)              │ │
│  │          Migratus migrations                   │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
         │
    ┌────▼────┐
    │   ECR   │
    │ (images)│
    └─────────┘
```

## Tech Stack

| Layer          | Technology                          | Rationale                                                    |
|----------------|-------------------------------------|--------------------------------------------------------------|
| Backend        | Clojure + Ring + Reitit             | Data-driven routing, functional composition                  |
| Frontend       | ClojureScript + Reagent + Re-frame  | Reactive UI with unidirectional data flow                    |
| Database       | PostgreSQL                          | Relational model fits products/orders; ACID transactions     |
| Connection Pool| HikariCP                            | Industry-standard JDBC pool                                  |
| Migrations     | Migratus                            | SQL-based, versioned, runs on app startup                    |
| Auth           | JWT (HS256) + demo accounts         | Role-based access control, auto-login as admin               |
| Build (BE)     | deps.edn + tools.build              | Modern Clojure standard, composable aliases                  |
| Build (FE)     | shadow-cljs                         | Best ClojureScript build tool, npm interop                   |
| Styling        | Tailwind CSS                        | Utility-first, rapid prototyping, consistent design          |
| Containers     | Docker multi-stage build            | Minimal production image (JRE Alpine)                        |
| IaC            | Terraform                           | Declarative, reproducible infrastructure                     |
| CI/CD          | Azure DevOps Pipelines              | Parallel test/build stages, Terraform apply, auto-deploy     |
| Testing        | Kaocha + Cloverage                  | Clojure test runner with coverage reporting                  |

## Key Design Decisions

### 1. Clojure Full-Stack

I chose Clojure because the Client uses it. Rather than submitting a polished solution in my daily stack (.NET + Angular), I wanted to demonstrate willingness to learn and deliver in the team's actual technology. ClojureScript on the frontend (Reagent + Re-frame) keeps the entire codebase in one language and ecosystem.

### 2. Separate API + SPA

REST API backend with a ClojureScript SPA over server-rendered templates:
- Clear separation of concerns — backend is a pure API
- API-first design enables integrations of collaborative development

### 3. PostgreSQL

- Products, orders, and order items are naturally relational
- ACID transactions are critical for purchase operations (stock decrement)
- Search in Postgres using ILIKE and trigram indexing provides fast performance

### 4. CSV Import Security

The provided CSV contains intentional attack vectors. Instead of just parsing and inserting, I analyzed the file and built a layered defense pipeline with threat reporting:

**Threats detected and blocked:**
1. **XSS injection** (`<script>` tags) — entire row rejected and logged
2. **SQL injection** (`DROP TABLE`) — entire row rejected and logged; parameterized queries as second layer
3. **Threat reporting** — scans raw values before processing, logs every detected attack
4. **Formula injection** (`=`, `+`, `-`, `@`, `|` prefixes) — stripped to prevent spreadsheet execution

**Handler-level defenses:**
5. File type validation (`.csv` only)
6. File size limit (20MB)
7. Row limit (100K)
8. Nil file check
9. Magic byte validation (detects binary files disguised as CSV)

The same sanitization (XSS + formula stripping) is applied to CRUD API endpoints — not just imports.

The expectation is that the Client will run the import with many different threats and scenarios.
Since I also published this on a live AWS environment, I thought of ways to make it safer as the import
is a vulnerability point in the solution.

We would still need to discuss malware scanning and optimization for uploading large files using S3 and HTTP multipart if needed.

Finally, I created multiple files and shared them on this repo for convenience and testing.

**Test files:** See `docs/test-imports/` for 13 test CSVs covering all 9 defenses.

### 5. Authentication & RBAC

At first I added auth, thinking to show the use of Cognito or Auth0.
Then I realized it didn't add much and it could create problems if the Client would like to run
tests using Postman or an automated script. So I decided to strip that out.
You will see this in the git log.

I ended up just leaving a pseudo role security. The app **auto-logs in as admin** on page load — no login step needed. To explore different permission levels, use the **Switch Role** option in the avatar menu.

| Username | Role   | Permissions                                    |
|----------|--------|------------------------------------------------|
| admin    | admin  | Full access: CRUD products, import CSV, orders |
| buyer    | buyer  | Browse products, add to cart, place orders     |
| reader   | reader | Read-only: browse products, view orders        |

Architecture: JWT (HS256) with three hardcoded demo users. Middleware chain: `wrap-auth` → `wrap-require-auth` → `wrap-require-role`.

### 6. EC2 + Docker Compose

For a demo with a 2-3 week lifespan, EC2 t3.micro (~$8/month) beats ECS Fargate (~$35+/month). The app is containerized, so migration to ECS is straightforward if needed.

I also added migration scripts. I am familiar with Entity Framework and migrations. I was happy to see we have something similar in the Clojure ecosystem which works really well for a production setup.

### 7. HTTPS + ALB

Application Load Balancer with ACM certificate for TLS termination. HTTP redirects to HTTPS. EC2 security group only accepts traffic from the ALB.

## Running Locally

### Prerequisites

- Java 21+
- Node.js 20+
- Clojure CLI (deps.edn)
- Docker & Docker Compose
- [Babashka](https://github.com/babashka/babashka#installation) (optional, for `bb` task runner)

### Quick Start (Docker)

```bash
docker compose up --build
```

The app will be available at `http://localhost:8080`. It auto-logs in as admin — you can switch roles via the avatar menu in the top-right corner.

### Development Mode

I would recommend this for day to day development or convenient launch and play and see the code.

**One command (requires Babashka):**

```bash
npm install    # first time only
bb dev         # starts Postgres, backend nREPL, shadow-cljs, and Tailwind in parallel
```

This starts everything you need:
- PostgreSQL via Docker (port 5433, avoids conflict with local Postgres)
- Backend on port 8080
- shadow-cljs with hot-reload on port 3000
- Tailwind CSS watcher

**Manual setup (without Babashka):**

1. Start PostgreSQL:
```bash
docker compose -f docker-compose.dev.yml up -d
```

2. Install frontend dependencies:
```bash
npm install
```

3. Start the backend:
```bash
clj -M:dev:run
```

4. Start the frontend (in a separate terminal):
```bash
npx shadow-cljs watch app
```

5. Build Tailwind CSS (in a separate terminal):
```bash
npx tailwindcss -i resources/public/css/input.css -o resources/public/css/output.css --watch
```

- Backend API: `http://localhost:8080/api`
- Frontend dev server: `http://localhost:3000`

### Available Tasks

Run `bb tasks` to see all available commands:

```
dev            Start Postgres + backend + frontend + CSS in parallel
dev:backend    Start backend with nREPL on port 7888
dev:frontend   Start shadow-cljs watch with hot-reload
dev:css        Watch and rebuild Tailwind CSS
db:up          Start Postgres only
db:down        Stop Postgres
db:reset       Destroy Postgres volume and restart fresh
migrate        Run database migrations
test           Run all tests and launch coverage report
test:backend   Run backend tests
test:frontend  Run frontend tests
test:coverage  Run backend tests with coverage
build          Build everything for production
docker         Build and run with Docker (production mode)
clean          Remove build artifacts
repl           Start a REPL (connect editor to port 7888)
```

### Running Tests

```bash
bb test              # run all tests
bb test:backend      # backend only
bb test:frontend     # frontend only
bb test:coverage     # backend with coverage report
```

## API Endpoints

| Method | Endpoint              | Auth         | Description              |
|--------|-----------------------|--------------|--------------------------|
| GET    | /api/health           | None         | Health check             |
| POST   | /api/auth/login       | None         | Login (returns JWT)      |
| GET    | /api/auth/me          | Any role     | Current user info        |
| GET    | /api/products         | None         | List/search products     |
| GET    | /api/products/:id     | None         | Get product by ID        |
| POST   | /api/products         | Admin        | Create product           |
| PUT    | /api/products/:id     | Admin        | Update product           |
| DELETE | /api/products/:id     | Admin        | Delete product           |
| DELETE | /api/products/all     | Admin        | Delete all products      |
| POST   | /api/products/import  | Admin        | Import CSV               |
| GET    | /api/orders           | Any role     | List orders              |
| GET    | /api/orders/:id       | Any role     | Get order details        |
| POST   | /api/orders           | Admin, Buyer | Place order              |

### Search Parameters

`GET /api/products?q=laptop&category=Electronics&page=1&per-page=20`

## Project Structure

```
e-commerce/
├── src/
│   ├── clj/ecommerce/          # Backend
│   │   ├── core.clj            # Entry point
│   │   ├── config.clj          # Configuration (Aero)
│   │   ├── server.clj          # Jetty server (Mount)
│   │   ├── routes.clj          # Reitit router
│   │   ├── handlers/           # HTTP handlers
│   │   ├── services/           # Business logic
│   │   ├── middleware/         # Auth, error handling
│   │   └── db/                 # Database layer
│   └── cljs/ecommerce/         # Frontend
│       ├── core.cljs           # SPA entry point
│       ├── db.cljs             # App state
│       ├── events.cljs         # Re-frame events
│       ├── subs.cljs           # Re-frame subscriptions
│       ├── http.cljs           # API client
│       ├── routes.cljs         # Client-side routing
│       └── views/              # UI components
├── resources/
│   ├── config.edn              # App configuration
│   ├── migrations/             # SQL migrations
│   └── public/                 # Static assets
├── test/clj/                   # Backend tests
├── infra/                      # Terraform
├── pipelines/                  # Azure DevOps CI/CD
├── deps.edn                    # Backend dependencies
├── shadow-cljs.edn             # Frontend build
├── docker-compose.yml
├── Dockerfile
└── README.md
```
