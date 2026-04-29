package com.youthfit.policy.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 첨부 redirect 분기 비즈니스 로직.
 *
 * <ul>
 *   <li>storageKey + S3 presign 가능 → PresignRedirect (302)</li>
 *   <li>storageKey + presign 실패 (Local 등) → StreamResponse (200 stream)</li>
 *   <li>storageKey 없음 + 외부 URL 있음 → ExternalRedirect (302)</li>
 *   <li>셋 다 없음 → AttachmentNotFoundException (404)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectAttachmentService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final PolicyAttachmentRepository repository;
    private final AttachmentStorage storage;

    public AttachmentRedirectResult resolve(Long attachmentId) {
        PolicyAttachment attachment = repository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        String storageKey = attachment.getStorageKey();
        // storage 에 실제 파일이 있을 때만 presign/stream 시도. 컨테이너 재기동 등으로 local
        // 파일이 휘발되면 storageKey 만 남아있으니 storage.exists() 로 가드.
        if (storageKey != null && storage.exists(storageKey)) {
            Optional<String> presigned = storage.presign(storageKey, PRESIGN_TTL);
            if (presigned.isPresent()) {
                log.info("attachment-redirect presign id={} key={}", attachmentId, storageKey);
                return new AttachmentRedirectResult.PresignRedirect(presigned.get());
            }
            log.info("attachment-redirect stream id={} key={}", attachmentId, storageKey);
            String mediaType = attachment.getMediaType() == null
                    ? "application/octet-stream"
                    : attachment.getMediaType();
            return new AttachmentRedirectResult.StreamResponse(
                    storage.get(storageKey),
                    mediaType,
                    attachment.getName());
        }

        String externalUrl = attachment.getUrl();
        if (externalUrl != null && !externalUrl.isBlank()) {
            log.info("attachment-redirect external id={} url={} (storageKey={}, missing local file)",
                    attachmentId, externalUrl, storageKey);
            return new AttachmentRedirectResult.ExternalRedirect(externalUrl);
        }

        throw new AttachmentNotFoundException(attachmentId);
    }
}
