package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadService.class);

    private final PolicyAttachmentRepository repository;
    private final PolicyAttachmentApplicationService stateService;
    private final AttachmentStorage storage;
    private final AttachmentDownloader downloader;

    @Setter
    @Value("${attachment.download.max-size-mb:50}")
    private int maxSizeMb;

    @Setter
    @Value("#{'${attachment.mime-whitelist:application/pdf,application/x-hwp,application/haansofthwp,application/vnd.hancom.hwp,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/html,text/plain}'.split(',')}")
    private Set<String> mimeWhitelist;

    @Async("attachmentDownloadExecutor")
    public void downloadForPolicyAsync(Long policyId) {
        List<PolicyAttachment> attachments = repository.findByPolicyId(policyId);
        for (PolicyAttachment a : attachments) {
            if (a.getExtractionStatus() == AttachmentStatus.PENDING) {
                downloadOne(a.getId());
            }
        }
    }

    public void downloadOne(Long attachmentId) {
        PolicyAttachment a = repository.findById(attachmentId).orElse(null);
        if (a == null) {
            log.warn("attachment not found: {}", attachmentId);
            return;
        }

        try {
            stateService.markDownloading(attachmentId);
        } catch (IllegalStateException e) {
            log.debug("attachment already in flight: {}", attachmentId);
            return;
        }

        if (!isAllowed(a.getMediaType())) {
            stateService.markSkipped(attachmentId, SkipReason.UNSUPPORTED_MIME);
            return;
        }

        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        try (AttachmentDownloader.DownloadedFile file = downloader.download(a.getUrl(), maxBytes)) {
            String key = buildStorageKey(a);
            StorageReference ref = storage.put(file.stream(), key, a.getMediaType());
            stateService.markDownloaded(attachmentId, ref.key(), ref.sha256Hex());
            log.info("downloaded attachment: id={} key={} bytes={}", attachmentId, ref.key(), ref.sizeBytes());
        } catch (AttachmentDownloader.OversizedException e) {
            stateService.markSkipped(attachmentId, SkipReason.OVERSIZED);
            log.info("attachment oversized skipped: id={}", attachmentId);
        } catch (Exception e) {
            stateService.markFailed(attachmentId, e.getClass().getSimpleName() + ": " + safeMessage(e));
            log.warn("attachment download failed: id={} err={}", attachmentId, e.getMessage());
        }
    }

    private boolean isAllowed(String mediaType) {
        return mediaType != null && mimeWhitelist.contains(mediaType.toLowerCase());
    }

    private String buildStorageKey(PolicyAttachment a) {
        LocalDate now = LocalDate.now();
        String ext = inferExt(a.getMediaType(), a.getName());
        return String.format("attachments/%d/%02d/%d-%s.%s",
                now.getYear(), now.getMonthValue(), a.getId(),
                UUID.randomUUID().toString().substring(0, 8), ext);
    }

    private String inferExt(String mediaType, String name) {
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (mediaType == null ? "" : mediaType.toLowerCase()) {
            case "application/pdf" -> "pdf";
            case "application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp" -> "hwp";
            case "text/html" -> "html";
            case "text/plain" -> "txt";
            default -> "bin";
        };
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "no message" : (m.length() > 200 ? m.substring(0, 200) : m);
    }
}
