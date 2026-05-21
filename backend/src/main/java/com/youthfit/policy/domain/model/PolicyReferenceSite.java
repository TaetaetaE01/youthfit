package com.youthfit.policy.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyReferenceSite(
        String name,
        String url,
        PolicyReferenceSiteSource source
) {
    @JsonCreator
    public PolicyReferenceSite(
            @JsonProperty("name") String name,
            @JsonProperty("url") String url,
            @JsonProperty("source") PolicyReferenceSiteSource source
    ) {
        this.name = name;
        this.url = url;
        this.source = source == null ? PolicyReferenceSiteSource.AUTO : source;
    }

    public static PolicyReferenceSite auto(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.AUTO);
    }

    public static PolicyReferenceSite admin(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.ADMIN);
    }
}
