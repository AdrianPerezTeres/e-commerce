#!/bin/bash
set -euo pipefail

yum update -y
yum install -y docker git
systemctl enable docker
systemctl start docker

curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

mkdir -p /opt/ecommerce
cat > /opt/ecommerce/.env <<EOF
DB_PASSWORD=${db_password}
DB_USER=ecommerce
DB_NAME=ecommerce
DB_HOST=db
DB_PORT=5432
PORT=8080
EOF

cat > /opt/ecommerce/docker-compose.yml <<'COMPOSE'
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: $${DB_NAME}
      POSTGRES_USER: $${DB_USER}
      POSTGRES_PASSWORD: $${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${DB_USER}"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    image: ghcr.io/${GITHUB_REPO:-ecommerce/ecommerce}:latest
    ports:
      - "80:8080"
    env_file: .env
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
COMPOSE
