package com.youthfit.policy.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_attachment")
public class PolicyAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "media_type", length = 100)
    private String mediaType;

    @Builder
    private PolicyAttachment(String name, String url, String mediaType) {
        this.name = name;
        this.url = url;
        this.mediaType = mediaType;
    }

    void assignTo(Policy policy) {
        this.policy = policy;
    }
}
