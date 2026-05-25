# ──────────── ACM certificate (us-east-1, for CloudFront) ────────────
# CloudFront viewer cert 요건: us-east-1 ACM, DNS 검증.
# 와일드카드 + apex 동시 cover: youthfit.xyz + *.youthfit.xyz

resource "aws_acm_certificate" "cloudfront" {
  provider = aws.use1

  domain_name               = "youthfit.xyz"
  subject_alternative_names = ["*.youthfit.xyz"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "youthfit-prod-cloudfront-cert"
  }
}

# ──────────── DNS validation records (Route 53) ────────────
# ACM 가 발급 시 검증 CNAME 을 알려준다. 같은 계정의 Route 53 이라 자동 생성.

resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = var.route53_zone_id
}

# ──────────── Wait for validation ────────────
# 이 리소스가 완료되면 인증서가 `ISSUED` 상태.

resource "aws_acm_certificate_validation" "cloudfront" {
  provider = aws.use1

  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]

  timeouts {
    create = "20m"
  }
}
