import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicyStatus } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  status?: PolicyStatus | '';
  regions?: string[];
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, status, regions, page = 0, size = 6 } = params;
  const regionsKey = regions && regions.length > 0 ? regions.join(',') : '';

  return useQuery({
    queryKey: ['policies', { keyword, category, status, regions: regionsKey, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, { status: status || undefined, page, size })
        : fetchPolicies({
            category: category || undefined,
            status: status || undefined,
            regions: regions && regions.length > 0 ? regions : undefined,
            page,
            size,
          }),
    placeholderData: keepPreviousData,
  });
}
