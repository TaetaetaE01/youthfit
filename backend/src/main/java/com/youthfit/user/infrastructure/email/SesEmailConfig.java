package com.youthfit.user.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "ses")
public class SesEmailConfig {

    @Bean
    public SesV2Client sesV2Client(
            @Value("${youthfit.email.ses.region}") String region,
            @Value("${youthfit.email.ses.access-key-id}") String accessKeyId,
            @Value("${youthfit.email.ses.secret-access-key}") String secretAccessKey) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }
}
