package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.domain.model.PolicyPeriod;

public interface PolicyPeriodLlmProvider {

    PolicyPeriod extractPeriod(String title, String body);
}
