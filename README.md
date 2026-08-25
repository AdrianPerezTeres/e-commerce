# E-Commerce Application

A full-stack e-commerce application built with Clojure (backend) and ClojureScript (frontend).

**Live site:** [https://ecommerce.appliedprogramming.io](https://ecommerce.appliedprogramming.io)

**CSV file downloaded:** August 18, 2026

**Presentation:** [Executive Summary (PowerPoint)](docs/E-Commerce%20Code%20Challenge.pptx)

> *In the era of AI we know any specific requirement can be achieved easily. This challenge is not focused on just completing it but we want you to ask the right questions and guide AI to make use of your experience and foreseeing skills.*
>
> I leaned into this — used AI throughout, guided by my experience shipping production systems. The decisions below reflect what I'd push for on a real project: security analysis of the CSV file, infrastructure as code, CI/CD, HTTPS, and auth. Not because the spec asked for it, but because that's what production needs.

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
| Containers     | Docker multi-stage build             | Minimal production image (JRE Alpine)                        |
| IaC            | Terraform                           | Declarative, reproducible infrastructure                     |
| CI/CD          | Azure DevOps Pipelines              | Parallel test/build stages, Terraform apply, auto-deploy     |
| Testing        | Kaocha + Cloverage                  | Clojure test runner with coverage reporting                  |

## Key Design Decisions

### 1. Clojure Full-Stack

I chose Clojure because the Client uses it. Rather than submitting a polished solution in my daily stack (.NET + Angular), I wanted to demonstrate willingness to learn and deliver in the team's actual technology. ClojureScript on the frontend (Reagent + Re-frame) keeps the entire codebase in one language and ecosystem.

### 2. Separate API + SPA

REST API backend with a ClojureScript SPA over server-rendered templates:
- Clear separation of concerns — backend is a pure API
- API-first design enables future clients (mobile, integrations)
- Re-frame state management showcases the ClojureScript ecosystem

### 3. PostgreSQL

- Products, orders, and order items are naturally relational
- ACID transactions are critical for purchase operations (stock decrement)
- PostgreSQL's ILIKE and trigram indexing support product search

### 4. CSV Import Security

The provided CSV contains intentional attack vectors. Rather than just parsing and inserting, I analyzed the file and built a layered defense pipeline with threat reporting:

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

**Test files:** See `docs/test-imports/` for 13 test CSVs covering all 9 defenses.

### 5. Authentication & RBAC

The challenge didn't require auth. I added it because real e-commerce apps need access control. The app **auto-logs in as admin** on page load — no login step needed. To explore different permission levels, use the **Switch Role** option in the avatar menu.

| Username | Password   | Role   | Permissions                                    |
|----------|------------|--------|------------------------------------------------|
| admin    | admin123   | admin  | Full access: CRUD products, import CSV, orders |
| buyer    | buyer123   | buyer  | Browse products, add to cart, place orders      |
| reader   | reader123  | reader | Read-only: browse products, view orders        |

Architecture: JWT (HS256) with three hardcoded demo users. Middleware chain: `wrap-auth` → `wrap-require-auth` → `wrap-require-role`.

### 6. EC2 + Docker Compose

For a demo with a 2-3 week lifespan, EC2 t3.micro (~$8/month) beats ECS Fargate (~$35+/month). The app is containerized, so migration to ECS is straightforward if needed.

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
test           Run all tests
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
