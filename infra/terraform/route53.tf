# ──────────── Route 53 records ────────────
# apex + www → CloudFront (Alias A/AAAA)
# api → EC2 EIP (A)
# n8n → 의도적으로 미생성 (n8n 미배포)

resource "aws_route53_record" "apex_a" {
  zone_id = var.route53_zone_id
  name    = "youthfit.xyz"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "apex_aaaa" {
  zone_id = var.route53_zone_id
  name    = "youthfit.xyz"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www_a" {
  zone_id = var.route53_zone_id
  name    = "www.youthfit.xyz"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www_aaaa" {
  zone_id = var.route53_zone_id
  name    = "www.youthfit.xyz"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "api_a" {
  zone_id = var.route53_zone_id
  name    = "api.youthfit.xyz"
  type    = "A"
  ttl     = 300
  records = [aws_eip.web.public_ip]
}
