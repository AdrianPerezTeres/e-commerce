output "app_public_ip" {
  description = "Public IP of the EC2 instance"
  value       = aws_eip.app.public_ip
}

output "app_url" {
  description = "Application URL"
  value       = "http://${aws_eip.app.public_ip}"
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = aws_ecr_repository.app.repository_url
}

output "app_https_url" {
  description = "Application HTTPS URL"
  value       = "https://${var.domain_name}"
}

output "alb_dns_name" {
  description = "ALB DNS name (use as CNAME target for domain)"
  value       = aws_lb.app.dns_name
}

output "acm_validation_records" {
  description = "ACM DNS validation records — add these as CNAMEs in Route 53"
  value = {
    for dvo in aws_acm_certificate.app.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }
}
