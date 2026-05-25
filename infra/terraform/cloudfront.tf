# ──────────── Origin Access Control ────────────
# CloudFront → S3 인증. Legacy OAI 가 아닌 OAC (SigV4) 사용.

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "youthfit-web-oac"
  description                       = "OAC for youthfit-web-prod S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ──────────── CloudFront distribution ────────────

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  comment             = "youthfit-web-prod"
  price_class         = "PriceClass_200" # NA + EU + APAC (Korea 포함). All 보다 저렴.
  http_version        = "http2and3"

  aliases = ["youthfit.xyz", "www.youthfit.xyz"]

  origin {
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_id                = "s3-youthfit-web"
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-youthfit-web"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # AWS managed policy: CachingOptimized (기본 1d, max 1y)
    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"

    # AWS managed policy: SecurityHeadersPolicy
    response_headers_policy_id = "67f7725c-6f97-4210-82d7-5512b31e9d03"
  }

  # SPA fallback: 403/404 (S3 가 client-side route 에 대해 returnss)
  # → /index.html 을 200 으로 반환. 단 /index.html 은 별도 캐시 정책 권장.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = {
    Name = "youthfit-prod-web"
  }
}
