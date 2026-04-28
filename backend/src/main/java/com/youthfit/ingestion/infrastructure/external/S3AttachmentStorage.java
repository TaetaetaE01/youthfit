package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "s3")
@RequiredArgsConstructor
public class S3AttachmentStorage implements AttachmentStorage {

    private final S3Client s3;

    @Value("${attachment.storage.s3.bucket}")
    private String bucket;

    @Override
    public StorageReference put(InputStream content, String key, String mediaType) {
        try {
            byte[] bytes = content.readAllBytes();
            String hex = sha256Hex(bytes);
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(mediaType)
                            .build(),
                    RequestBody.fromBytes(bytes));
            return new StorageReference(key, bytes.length, hex);
        } catch (IOException e) {
            throw new IllegalStateException("s3 put failed: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
