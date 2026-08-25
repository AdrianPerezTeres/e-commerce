resource "aws_ssm_parameter" "db_password" {
  name        = "/ecommerce/db-password"
  description = "Database password for the ecommerce application"
  type        = "SecureString"
  value       = var.db_password

  tags = { Name = "ecommerce-db-password" }
}
