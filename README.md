# E-Commerce Application

A full-stack e-commerce application built with Clojure (backend) and ClojureScript (frontend), demonstrating enterprise-grade architecture decisions, clean separation of concerns, and production-ready deployment infrastructure.

**CSV file downloaded:** August 18, 2026

**Presentation:** [Executive Summary (PowerPoint)](docs/E-Commerce%20Code%20Challenge.pptx) — 3 slides covering architecture, security analysis, and CI/CD infrastructure.

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
│  │  Ring + Reitit │ Auth (JWT/Cognito)            │ │
│  │  Handlers → Services → DB Layer (next.jdbc)    │ │
│  └──────────────────────┬─────────────────────────┘ │
│                         │                            │
│  ┌──────────────────────▼─────────────────────────┐ │
│  │          PostgreSQL 16 (HikariCP)              │ │
│  │          Migratus migrations                   │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
         │                              │
    ┌────▼────┐                   ┌─────▼─────┐
    │   ECR   │                   │  Cognito  │
    │ (images)│                   │  (auth)   │
    └─────────┘                   └───────────┘
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
| CI/CD          | Azure DevOps Pipelines              | Parallel test/build stages, Terraform apply, auto-deploy     |
| Testing        | Kaocha + Cloverage                  | Clojure test runner with coverage reporting                  |

## Why Go Beyond the Requirements?

The challenge asked for a CRUD app with CSV import — achievable in a weekend with any framework. I deliberately chose to go further to demonstrate the kind of thinking and ownership I bring to real projects:

- **Clojure instead of .NET/Angular** — I could have used my daily stack (.NET + Angular) and finished faster, but the Client uses Clojure. Learning and delivering in an unfamiliar language within a tight deadline shows adaptability and commitment.
- **AWS deployment instead of local-only** — Not required, but a running production URL proves the app works beyond `localhost`. It also demonstrates infrastructure skills (Terraform, CI/CD, Docker, SSM) that are hard to show in a code review alone.
- **HTTPS + ALB + ACM instead of plain HTTP** — A simple EC2 with port 80 would have worked. Adding an Application Load Balancer with a TLS certificate shows understanding of production architecture.
- **Cognito (Auth0-style) instead of no auth** — The challenge didn't require authentication. Adding JWT-based RBAC with three roles and a Cognito-ready backend demonstrates security awareness. The simpler path would have been no login at all.
- **9 security defenses instead of basic validation** — I could have just parsed the CSV and inserted rows. Instead, I analyzed the provided file for attack vectors (XSS, SQL injection, formula injection) and built a layered defense pipeline with threat reporting.
- **CI/CD pipeline instead of manual deploys** — Azure DevOps with parallel test stages, coverage reporting, Terraform apply, and zero-touch SSM deployment. Every push to main builds, tests, validates, and deploys automatically.

None of these were required. All of them reflect how I approach real work — not just meeting the spec, but thinking about what a production system actually needs.

## Key Design Decisions

### 1. Clojure Full-Stack (vs .NET + Angular)

I chose Clojure because the Client uses it. Rather than submitting a polished solution in a familiar stack, I wanted to demonstrate willingness to learn and deliver in the team's actual technology. ClojureScript on the frontend (Reagent + Re-frame) keeps the entire codebase in one language and ecosystem.

**Alternative considered:** .NET backend + Angular frontend — my daily stack, would have been faster but wouldn't demonstrate alignment with the Client's technology choices.

### 2. Separate API + SPA (vs Server-Rendered)

Chose a REST API backend with a ClojureScript SPA over server-rendered templates because:
- Clear separation of concerns — backend is a pure API
- API-first design enables future clients (mobile, integrations)
- Demonstrates architectural thinking beyond "just make it work"
- Re-frame state management showcases the ClojureScript ecosystem

**Alternative considered:** Server-rendered with Hiccup + HTMX — simpler but less demonstrative of enterprise architecture.

### 3. PostgreSQL (vs SQLite/MongoDB)

- Products, orders, and order items are naturally relational
- ACID transactions are critical for purchase operations (stock decrement)
- PostgreSQL's ILIKE and trigram indexing support product search
- Production-grade database that scales

**Alternative considered:** SQLite for zero-config simplicity, or MongoDB for schema flexibility — but relational integrity and transactional guarantees were more important for an e-commerce domain.

### 4. CSV Import — 9 Security Defenses (vs Basic Parsing)

The provided CSV contains intentional data quality challenges and security attack vectors. The simpler approach would have been to parse and insert — instead, I analyzed the file for attack vectors and built a layered defense pipeline with threat reporting. Our import implements 9 numbered defenses (see `csv_import.clj` and `handlers/products.clj`):

**Security threats detected and neutralized:**
1. **XSS injection** (`<script>` tags) — stripped via HTML tag regex. React/Reagent auto-escapes on render as second layer
2. **Threat reporting** — scans raw values before sanitization, logs every detected attack with type, line, and field
3. **SQL injection** (`DROP TABLE`) — parameterized queries (next.jdbc) prevent injection at the driver level
4. **Formula injection** (`=`, `+`, `-`, `@`, `|` prefixes) — stripped to prevent Excel/Sheets execution if data is exported

**Additional defenses (handler level):**
5. **File type validation** — only `.csv` files accepted, rejects EXE/binary/other uploads
6. **File size limit** — 20MB max to prevent memory exhaustion
7. **Row limit** — 100,000 rows max to prevent DB flooding
8. **Nil file check** — rejects requests with no file uploaded

**Data quality handling:**
- **Invalid prices** (`"free"`, `"$29.99"`) — coercion pipeline strips currency symbols, rejects non-numeric values
- **Negative stock** — rejected with error, as negative stock is logically invalid
- **Duplicate SKUs** — `ON CONFLICT (sku) DO NOTHING`, first occurrence wins, duplicates reported
- **Empty rows** — detected and skipped silently
- **Missing required fields** — row skipped with descriptive error per field

The import returns a detailed report: imported count, skipped count, duplicate details, per-row errors with line numbers, and a **"Threats Blocked"** panel showing every detected attack with color-coded badges.

**Test files:** See `docs/test-imports/` for 13 test CSVs covering all 9 defenses — XSS payloads, SQL injection, formula injection, binary files disguised as CSV, oversized files, and more. Run `docs/test-imports/generate-bomb.sh` to create the 1M-row and 25MB stress test files.

### 5. Authentication & Cognito (vs No Auth)

The challenge didn't require authentication. I added it because real e-commerce apps need access control, and it demonstrates security architecture. The app implements JWT-based authentication with three roles. In **development mode** (`AUTH_REQUIRED=false`, default), it auto-authenticates as admin so reviewers don't need to log in. In **Docker/production** (`AUTH_REQUIRED=true`), it requires login and shows three demo accounts on the login page.

**Alternative considered:** No authentication at all — would have saved time but missed the opportunity to demonstrate RBAC, JWT middleware, and Cognito integration.

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

### 6. deps.edn (vs Leiningen)

- deps.edn is the official Clojure CLI tool, actively maintained by the core team
- More explicit and composable than Leiningen's convention-heavy approach
- Modern projects in the Clojure ecosystem have converged on deps.edn

**Alternative considered:** Leiningen — more convention-based and widely documented, but deps.edn is the modern standard.

### 7. EC2 + Docker Compose (vs ECS Fargate)

For a demo with a 2-3 week lifespan:
- EC2 t3.micro: ~$8/month vs ECS + ALB: ~$35+/month
- docker-compose is simpler to debug and update
- The architecture is designed to migrate to ECS if needed (containerized, environment-driven config)

**Alternative considered:** ECS Fargate for fully managed containers — production-grade but overkill for a demo. The app is already containerized, so migration is straightforward.

### 8. HTTPS + ALB (vs Plain HTTP)

- Application Load Balancer with ACM certificate for TLS termination
- HTTP automatically redirects to HTTPS
- EC2 security group only accepts traffic from the ALB — not directly from the internet

**Alternative considered:** Plain HTTP on the Elastic IP — functional but doesn't demonstrate understanding of production security requirements.

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
# Create terraform.tfvars with your values (db_password, key_pair_name, ecr_registry)

terraform init
terraform plan
terraform apply
```

This provisions:
- VPC with 2 public subnets (multi-AZ)
- Application Load Balancer with HTTPS (ACM certificate)
- EC2 instance with Docker pre-installed
- Elastic IP
- Security groups (ALB: public HTTP/HTTPS, EC2: ALB-only + SSH)
- ACM certificate for `ecommerce.appliedprogramming.io`
- Cognito User Pool with app client
- ECR repository for Docker images

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
├── pipelines/                  # Azure DevOps CI/CD
├── deps.edn                    # Backend dependencies
├── shadow-cljs.edn             # Frontend build
├── docker-compose.yml
├── Dockerfile
└── README.md
```
