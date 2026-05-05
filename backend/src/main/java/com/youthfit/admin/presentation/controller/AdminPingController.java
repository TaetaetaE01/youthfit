package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.AdminPingResponse;
import com.youthfit.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPingController implements AdminPingApi {

    @GetMapping("/ping")
    @Override
    public ResponseEntity<ApiResponse<AdminPingResponse>> ping() {
        return ResponseEntity.ok(ApiResponse.ok(AdminPingResponse.pong()));
    }
}
