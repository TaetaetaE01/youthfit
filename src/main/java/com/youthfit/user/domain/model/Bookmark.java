package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bookmark", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookmark_user_policy", columnNames = {"user_id", "policy_id"})
})
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    public Bookmark(Long userId, Long policyId) {
        this.userId = userId;
        this.policyId = policyId;
    }
}
