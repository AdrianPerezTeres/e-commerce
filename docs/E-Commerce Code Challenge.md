# E-Commerce Code Challenge — Executive Summary

---

## Slide 1: Architectural Decisions

- **Clojure (Full-Stack)** — I chose Clojure because the Client already uses it. This challenge was an opportunity to familiarize myself with the stack and embrace functional programming. I used ClojureScript on the frontend for consistency across the codebase, leveraging Reagent (a React wrapper) for a modern SPA experience.

- **PostgreSQL** — Products and orders form a relational domain with structured relationships, constraints, and transactional integrity. A relational database was the natural fit over a document store.

- **AWS** — the Client uses AWS, and I have hands-on experience with it. This made it the right choice to demonstrate real-world deployment skills on a familiar platform.

- **Babashka** — I added Babashka as a task runner (`bb dev`, `bb test`) to simplify the local development workflow. Research confirmed this is a standard practice across Clojure projects.

---

## Slide 2: Security & Threat Analysis

- **Beyond the Happy Path** — I wanted to demonstrate the ability to review, analyze, and defend against real threats — not just validate that the happy path works, but expand to a wider range of scenarios and outcomes.

- **CSV Import as Attack Surface** — Being the core functionality, the CSV import is also the most vulnerable entry point of the solution. I identified and neutralized two attack types embedded in the provided CSV:
  - **XSS (Cross-Site Scripting)** — Script tags injected as product names, stripped via regex before storage and auto-escaped by React on render.
  - **SQL Injection** — Malicious SQL in product names, neutralized by parameterized queries that treat all input as literal data.

- **Threat Reporting** — I added a detection layer that scans raw values before sanitization, logging every identified attack. The import results UI displays a "Threats Blocked" panel with type, line number, and action taken — proving the system catches and reports threats transparently. The same sanitization is applied to CRUD API endpoints, rejecting malicious content with detailed error responses.

- **Real-World Validation** — I deployed the application to the web to test against scenarios that only surface in production: larger files, concurrent requests, and more robust attack patterns.

---

## Slide 3: CI/CD, AWS & Infrastructure as Code

- **Not Requested, But Demonstrated** — The challenge didn't ask for cloud infrastructure, but I wanted to prove my knowledge as an end-to-end senior developer with experience building enterprise-grade applications.

- **AWS Infrastructure** — An EC2 instance running Docker Compose for the application, ALB with ACM for HTTPS termination, and ECR for Docker image storage. JWT-based auth with role-based demo accounts (admin/buyer/reader) keeps authentication simple and testable — no external identity provider needed.

- **Infrastructure as Code** — All AWS resources are defined in Terraform, version-controlled alongside the application. The pipeline runs `terraform plan` and `terraform apply` automatically on every merge to main.

- **CI/CD Pipeline (Azure DevOps)** — Automated builds, backend and frontend tests with coverage reporting, Docker image builds, and zero-touch deployments to EC2 via SSM. Every push to main triggers the full pipeline: build, test, validate, deploy.

- **Quality Gates** — Test coverage and Terraform validation run on every commit, providing effortless quality assurance as a built-in part of the development workflow.

The job description mentioned AWS, CI/CD, and infrastructure — I wanted to express not just familiarity but practical experience delivering production-ready solutions across the full stack.
