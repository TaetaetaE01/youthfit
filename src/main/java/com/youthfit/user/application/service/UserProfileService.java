package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.user.application.dto.command.UpdateProfileCommand;
import com.youthfit.user.application.dto.result.UserProfileResult;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResult findMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        return UserProfileResult.from(user);
    }

    @Transactional
    public UserProfileResult updateMyProfile(Long userId, UpdateProfileCommand command) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.updateProfile(command.getNickname(), command.getProfileImageUrl());
        return UserProfileResult.from(user);
    }
}
