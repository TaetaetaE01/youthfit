package com.youthfit.ingestion.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "s3")
public class AttachmentStorageS3Config {

    @Bean
    public S3Client attachmentS3Client(
            @Value("${attachment.storage.s3.region:ap-northeast-2}") String region,
            @Value("${attachment.storage.s3.access-key-id:}") String accessKeyId,
            @Value("${attachment.storage.s3.secret-access-key:}") String secretAccessKey) {

        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            // S3 전용 IAM 키 명시 주입 (.env 의 S3_ACCESS_KEY_ID / S3_SECRET_ACCESS_KEY)
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                    )
            );
        }
        // 키가 비어있으면 DefaultCredentialsProvider 체인 사용 (EC2/ECS IAM role, ~/.aws/credentials 등)

        return builder.build();
    }

    @Bean
    public S3Presigner attachmentS3Presigner(
            @Value("${attachment.storage.s3.region:ap-northeast-2}") String region,
            @Value("${attachment.storage.s3.access-key-id:}") String accessKeyId,
            @Value("${attachment.storage.s3.secret-access-key:}") String secretAccessKey) {

        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));

        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                    )
            );
        }

        return builder.build();
    }
}
