# E-Commerce Application

A full-stack e-commerce application built with Clojure (backend) and ClojureScript (frontend), demonstrating enterprise-grade architecture decisions, clean separation of concerns, and production-ready deployment infrastructure.

**CSV file downloaded:** August 22, 2026

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    Browser                          │
│         ClojureScript SPA (Reagent/Re-frame)        │
│         Client-side routing (Reitit)                │
│         Tailwind CSS                                │
└──────────────────────┬──────────────────────────────┘
                       │ REST/JSON + JWT
┌──────────────────────▼──────────────────────────────┐
│                  Clojure Backend                    │
│  Ring + Reitit │ Middleware (Auth, CORS, Multipart) │
│  Handlers → Services → DB Layer (next.jdbc)         │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              PostgreSQL (HikariCP pool)              │
│              Migratus migrations                     │
└─────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer          | Technology                          | Rationale                                                    |
|----------------|-------------------------------------|--------------------------------------------------------------|
| Backend        | Clojure + Ring + Reitit             | Data-driven routing, functional composition                  |
| Frontend       | ClojureScript + Reagent + Re-frame  | Reactive UI with unidirectional data flow                    |
| Database       | PostgreSQL                          | Relational model fits products/orders; ACID transactions     |
| Connection Pool| HikariCP                            | Industry-standard JDBC pool                                  |
| Migrations     | Migratus                            | SQL-based, versioned, runs on app startup                    |
| Auth           | JWT (local demo) / AWS Cognito      | Role-based access control with demo users                    |
| Build (BE)     | deps.edn + tools.build              | Modern Clojure standard, composable aliases                  |
| Build (FE)     | shadow-cljs                         | Best ClojureScript build tool, npm interop                   |
| Styling        | Tailwind CSS                        | Utility-first, rapid prototyping, consistent design          |
| Containers     | Docker multi-stage build             | Minimal production image (JRE Alpine)                        |
| IaC            | Terraform                           | Declarative, reproducible infrastructure                     |
| CI/CD          | GitHub Actions                      | Native GitHub integration, test + build + deploy pipeline    |
| Testing        | Kaocha + Cloverage                  | Clojure test runner with coverage reporting                  |

## Key Design Decisions

### 1. Separate API + SPA (vs Server-Rendered)

Chose a REST API backend with a ClojureScript SPA over server-rendered templates because:
- Clear separation of concerns — backend is a pure API
- API-first design enables future clients (mobile, integrations)
- Demonstrates architectural thinking beyond "just make it work"
- Re-frame state management showcases the ClojureScript ecosystem

**Alternative considered:** Server-rendered with Hiccup + HTMX — simpler but less demonstrative of enterprise architecture.

### 2. PostgreSQL (vs SQLite/MongoDB)

- Products, orders, and order items are naturally relational
- ACID transactions are critical for purchase operations (stock decrement)
- PostgreSQL's ILIKE and trigram indexing support product search
- Production-grade database that scales

### 3. CSV Import Validation Pipeline

The provided CSV contains intentional data quality challenges. Our import pipeline handles:

- **XSS injection** (`<script>` tags) — stripped via HTML tag removal. On the frontend, React/Reagent renders content safely by default (no `dangerouslySetInnerHTML`)
- **SQL injection** (`DROP TABLE`) — parameterized queries (next.jdbc) prevent SQL injection at the driver level. We never interpolate user input into SQL strings
- **Invalid prices** (`"free"`, `"$29.99"`) — coercion pipeline strips currency symbols, rejects non-numeric values
- **Negative stock** — rejected with error, as negative stock is logically invalid
- **Duplicate SKUs** — `ON CONFLICT (sku) DO NOTHING`, first occurrence wins, duplicates reported
- **Empty rows** — detected and skipped silently
- **Missing required fields** — row skipped with descriptive error per field

The import returns a detailed report: imported count, skipped count, duplicate details, and per-row errors with line numbers.

### 4. Authentication & Role-Based Access Control

The app implements JWT-based authentication with three roles. In **development mode** (`AUTH_REQUIRED=false`, default), it auto-authenticates as admin so reviewers don't need to log in. In **Docker/production** (`AUTH_REQUIRED=true`), it requires login and shows three demo accounts on the login page.

#### Demo Accounts

| Username | Password   | Role   | Permissions                                    |
|----------|------------|--------|------------------------------------------------|
| admin    | admin123   | admin  | Full access: CRUD products, import CSV, orders |
| buyer    | buyer123   | buyer  | Browse products, add to cart, place orders      |
| reader   | reader123  | reader | Read-only: browse products, view orders        |

#### Permission Matrix

| Action              | Admin | Buyer | Reader | Anonymous |
|---------------------|-------|-------|--------|-----------|
| Browse products     | Yes   | Yes   | Yes    | Yes       |
| Create/Edit product | Yes   | No    | No     | No        |
| Delete product      | Yes   | No    | No     | No        |
| Import CSV          | Yes   | No    | No     | No        |
| Add to cart         | Yes   | Yes   | No     | No        |
| Place order         | Yes   | Yes   | No     | No        |
| View orders         | Yes   | Yes   | Yes    | No        |

#### Architecture

- **Local JWT (HS256)**: Used by default for demo — no external dependencies needed
- **AWS Cognito (RS256)**: When `COGNITO_USER_POOL_ID` is set, switches to Cognito JWKS verification
- The middleware chain: `wrap-auth` (extract JWT) → `wrap-require-auth` (enforce login) → `wrap-require-role` (enforce permissions)

### 5. deps.edn (vs Leiningen)

- deps.edn is the official Clojure CLI tool, actively maintained by the core team
- More explicit and composable than Leiningen's convention-heavy approach
- Modern projects in the Clojure ecosystem have converged on deps.edn

### 6. EC2 + docker-compose (vs ECS Fargate)

For a demo with a 2-3 week lifespan:
- EC2 t3.micro: ~$8/month vs ECS + ALB: ~$35+/month
- docker-compose is simpler to debug and update
- The architecture is designed to migrate to ECS if needed (containerized, environment-driven config)

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

The app will be available at `http://localhost:8080`. You'll see a login page with three demo accounts (admin/buyer/reader) — click any to log in instantly.

### Development Mode

**One command (requires Babashka):**

```bash
npm install    # first time only
bb dev         # starts Postgres, backend nREPL, shadow-cljs, and Tailwind in parallel
```

This starts everything you need:
- PostgreSQL via Docker (port 5432)
- Backend with nREPL on port 7888 (connect your editor here)
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

### REPL-Driven Development

The idiomatic Clojure workflow. Start a REPL and control the app from your editor:

```bash
bb repl        # or: clj -M:dev -m nrepl.cmdline --port 7888
```

Then connect your editor (Calva, CIDER, Cursive) to port 7888 and evaluate:

```clojure
(go)           ;; start everything (server, db, migrations)
(stop)         ;; stop everything
(reset)        ;; stop, reload changed files, restart — instant feedback
```

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

### Building for Production

```bash
# Build frontend
npm run build:frontend

# Build backend uberjar
npm run build:backend

# Run the jar
java -jar target/ecommerce-0.1.0-standalone.jar
```

## Infrastructure Deployment

Infrastructure is managed with Terraform in the `infra/` directory.

```bash
cd infra
cp example.tfvars terraform.tfvars
# Edit terraform.tfvars with your values

terraform init
terraform plan
terraform apply
```

This provisions:
- VPC with public subnet
- EC2 instance with Docker pre-installed
- Elastic IP
- Security groups (HTTP/HTTPS/SSH)
- Cognito User Pool with app client

## API Endpoints

| Method | Endpoint              | Auth         | Description              |
|--------|-----------------------|--------------|--------------------------|
| GET    | /api/health           | None         | Health check             |
| GET    | /api/auth/config      | None         | Auth configuration       |
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
│   │   ├── middleware/         # Auth, etc.
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
├── .github/workflows/          # CI/CD
├── deps.edn                    # Backend dependencies
├── shadow-cljs.edn             # Frontend build
├── docker-compose.yml
├── Dockerfile
└── README.md
```
