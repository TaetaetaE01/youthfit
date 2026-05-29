package com.youthfit.admin.application.dto;

public record PolicyProcessingListCommand(
    String query,
    String region,
    PolicyProcessingFilter filter,
    PolicyProcessingSort sort,
    int page,
    int size
) {
    public PolicyProcessingListCommand {
        if (filter == null) filter = PolicyProcessingFilter.ALL;
        if (sort == null) sort = PolicyProcessingSort.UPDATED_DESC;
        if (size <= 0 || size > 200) size = 50;
        if (page < 0) page = 0;
    }
}
