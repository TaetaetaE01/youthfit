package com.youthfit.policy.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RedirectAttachmentService")
class RedirectAttachmentServiceTest {

    private final PolicyAttachmentRepository repo = Mockito.mock(PolicyAttachmentRepository.class);
    private final AttachmentStorage storage = Mockito.mock(AttachmentStorage.class);
    private final RedirectAttachmentService service = new RedirectAttachmentService(repo, storage);

    @Test
    @DisplayName("storageKey 가 있고 presign 가능 → PresignRedirect")
    void givenStorageKeyAndPresignAvailable_whenResolve_thenPresignRedirect() {
        PolicyAttachment att = mockAttachment(12L, "key-12", "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));
        Mockito.when(storage.exists("key-12")).thenReturn(true);
        Mockito.when(storage.presign(Mockito.eq("key-12"), Mockito.any(Duration.class)))
                .thenReturn(Optional.of("https://s3.aws/presigned"));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.PresignRedirect.class);
        assertThat(((AttachmentRedirectResult.PresignRedirect) result).url())
                .isEqualTo("https://s3.aws/presigned");
    }

    @Test
    @DisplayName("storageKey 는 있지만 presign 실패(Local 등) → StreamResponse")
    void givenStorageKeyButPresignEmpty_whenResolve_thenStreamResponse() {
        PolicyAttachment att = mockAttachment(12L, "key-12", "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));
        Mockito.when(storage.exists("key-12")).thenReturn(true);
        Mockito.when(storage.presign(Mockito.eq("key-12"), Mockito.any(Duration.class)))
                .thenReturn(Optional.empty());
        Mockito.when(storage.get("key-12"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.StreamResponse.class);
        var stream = (AttachmentRedirectResult.StreamResponse) result;
        assertThat(stream.mediaType()).isEqualTo("application/pdf");
        assertThat(stream.filename()).contains("x.pdf");
    }

    @Test
    @DisplayName("storageKey 가 없고 외부 URL 만 있음 → ExternalRedirect")
    void givenNoStorageKey_whenResolve_thenExternalRedirect() {
        PolicyAttachment att = mockAttachment(12L, null, "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.ExternalRedirect.class);
        assertThat(((AttachmentRedirectResult.ExternalRedirect) result).url())
                .isEqualTo("https://orig.example/x.pdf");
    }

    @Test
    @DisplayName("repository.findById 가 비어있으면 AttachmentNotFoundException")
    void givenNotFound_whenResolve_thenThrows() {
        Mockito.when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(99L))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    @DisplayName("storageKey 도 외부 URL 도 없으면 AttachmentNotFoundException")
    void givenNoStorageKeyAndNoExternalUrl_whenResolve_thenThrows() {
        PolicyAttachment att = mockAttachment(12L, null, null, "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.resolve(12L))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    private PolicyAttachment mockAttachment(Long id, String storageKey, String url, String mediaType) {
        PolicyAttachment a = Mockito.mock(PolicyAttachment.class);
        Mockito.when(a.getId()).thenReturn(id);
        Mockito.when(a.getStorageKey()).thenReturn(storageKey);
        Mockito.when(a.getUrl()).thenReturn(url);
        Mockito.when(a.getMediaType()).thenReturn(mediaType);
        Mockito.when(a.getName()).thenReturn(url == null ? "unknown" : url.substring(url.lastIndexOf('/') + 1));
        return a;
    }
}
