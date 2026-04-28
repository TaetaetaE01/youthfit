package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path basePath;

    public LocalAttachmentStorage(@Value("${attachment.storage.local-path:/data/attachments}") String localPath) {
        this.basePath = Path.of(localPath);
    }

    @Override
    public StorageReference put(InputStream content, String key, String mediaType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream dis = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(target)) {
                size = dis.transferTo(out);
            }
            String hex = HexFormat.of().formatHex(digest.digest());
            return new StorageReference(key, size, hex);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("local storage put failed: " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("local storage get failed: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    private Path resolve(String key) {
        return basePath.resolve(key.replace("..", "_")).normalize();
    }
}
