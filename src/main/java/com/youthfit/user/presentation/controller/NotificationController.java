package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.NotificationSettingResult;
import com.youthfit.user.application.service.NotificationSettingService;
import com.youthfit.user.presentation.dto.request.UpdateNotificationSettingRequest;
import com.youthfit.user.presentation.dto.response.NotificationSettingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationSettingService notificationSettingService;

    @GetMapping("/settings")
    @Override
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> findNotificationSetting(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        NotificationSettingResult result = notificationSettingService.findNotificationSetting(userId);
        return ResponseEntity.ok(ApiResponse.ok(NotificationSettingResponse.from(result)));
    }

    @PutMapping("/settings")
    @Override
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateNotificationSetting(
            Authentication authentication,
            @Valid @RequestBody UpdateNotificationSettingRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        NotificationSettingResult result = notificationSettingService.updateNotificationSetting(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(NotificationSettingResponse.from(result)));
    }
}
