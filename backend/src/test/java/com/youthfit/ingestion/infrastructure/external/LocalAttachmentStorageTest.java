package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.StorageReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAttachmentStorageTest {

    @Test
    void put_은_파일을_저장하고_sha256과_크기를_반환(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        StorageReference ref = sut.put(new ByteArrayInputStream(data), "test/key.txt", "text/plain");

        assertThat(ref.key()).isEqualTo("test/key.txt");
        assertThat(ref.sizeBytes()).isEqualTo(5);
        assertThat(ref.sha256Hex())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void get_은_저장된_바이트를_반환(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        sut.put(new ByteArrayInputStream("hi".getBytes()), "k", "text/plain");
        try (InputStream in = sut.get("k")) {
            assertThat(in.readAllBytes()).isEqualTo("hi".getBytes());
        }
    }

    @Test
    void exists_는_저장유무에_따라_true_false(@TempDir Path tmp) throws Exception {
        LocalAttachmentStorage sut = new LocalAttachmentStorage(tmp.toString());
        assertThat(sut.exists("k")).isFalse();
        sut.put(new ByteArrayInputStream("x".getBytes()), "k", "text/plain");
        assertThat(sut.exists("k")).isTrue();
    }
}
