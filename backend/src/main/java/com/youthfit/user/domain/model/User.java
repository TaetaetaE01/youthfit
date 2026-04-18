package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_auth_provider_provider_id",
                columnNames = {"auth_provider", "provider_id"}
        )
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Builder
    private User(String email, String nickname, String profileImageUrl,
                 AuthProvider authProvider, String providerId) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.role = Role.USER;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname == null || nickname.isBlank()) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "닉네임은 비어있을 수 없습니다");
        }
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "이메일은 비어있을 수 없습니다");
        }
        this.email = email;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }
}
