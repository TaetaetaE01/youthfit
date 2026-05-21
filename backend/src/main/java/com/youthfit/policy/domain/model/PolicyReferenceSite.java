package com.youthfit.policy.domain.model;

public record PolicyReferenceSite(
        String name,
        String url,
        PolicyReferenceSiteSource source
) {
    public PolicyReferenceSite {
        if (source == null) {
            source = PolicyReferenceSiteSource.AUTO;
        }
    }

    public static PolicyReferenceSite auto(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.AUTO);
    }

    public static PolicyReferenceSite admin(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.ADMIN);
    }
}
