#!/bin/bash
set -euo pipefail

yum update -y
yum install -y docker git unzip
systemctl enable docker
systemctl start docker

curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

mkdir -p /opt/ecommerce

cat > /opt/ecommerce/ecr-login.sh <<'ECRLOGIN'
#!/bin/bash
aws ecr get-login-password --region ${aws_region} | \
  docker login --username AWS --password-stdin ${ecr_registry}
ECRLOGIN
chmod +x /opt/ecommerce/ecr-login.sh
echo "0 */6 * * * /opt/ecommerce/ecr-login.sh" | crontab -

/opt/ecommerce/ecr-login.sh || true

cat > /opt/ecommerce/.env <<EOF
DB_PASSWORD=${db_password}
DB_USER=ecommerce
DB_NAME=ecommerce
DB_HOST=db
DB_PORT=5432
PORT=8080
EOF

cat > /opt/ecommerce/docker-compose.yml <<COMPOSE
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: \$${DB_NAME}
      POSTGRES_USER: \$${DB_USER}
      POSTGRES_PASSWORD: \$${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \$${DB_USER}"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    image: ${ecr_registry}/ecommerce:latest
    ports:
      - "80:8080"
    env_file: .env
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
COMPOSE
