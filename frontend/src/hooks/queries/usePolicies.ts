import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicySortType, PolicyStatus } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  status?: PolicyStatus | '';
  regionCode?: string;
  sortType?: PolicySortType;
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, status, regionCode, sortType, page = 0, size = 6 } = params;

  return useQuery({
    queryKey: ['policies', { keyword, category, status, regionCode, sortType, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, page, size)
        : fetchPolicies({ category: category || undefined, status: status || undefined, regionCode: regionCode || undefined, sortType, page, size }),
    placeholderData: keepPreviousData,
  });
}
