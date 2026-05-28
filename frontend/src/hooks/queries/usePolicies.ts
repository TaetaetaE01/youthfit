import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { fetchPolicies, searchPolicies } from '@/apis/policy.api';
import type { PolicyCategory, PolicyStatus, SourceType } from '@/types/policy';

interface UsePoliciesParams {
  keyword?: string;
  category?: PolicyCategory | '';
  source?: SourceType | '';
  status?: PolicyStatus | '';
  regions?: string[];
  page?: number;
  size?: number;
}

export function usePolicies(params: UsePoliciesParams) {
  const { keyword, category, source, status, regions, page = 0, size = 6 } = params;
  const regionsKey = regions && regions.length > 0 ? regions.join(',') : '';

  return useQuery({
    queryKey: ['policies', { keyword, category, source, status, regions: regionsKey, page, size }],
    queryFn: () =>
      keyword
        ? searchPolicies(keyword, { status: status || undefined, page, size })
        : fetchPolicies({
            category: category || undefined,
            source: source || undefined,
            status: status || undefined,
            regions: regions && regions.length > 0 ? regions : undefined,
            page,
            size,
          }),
    placeholderData: keepPreviousData,
  });
}
