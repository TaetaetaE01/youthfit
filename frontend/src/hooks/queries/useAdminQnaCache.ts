import { useQuery } from '@tanstack/react-query';
import {
  getQnaCacheKpi,
  getQnaCacheDailyStats,
  listQnaCacheLookups,
  type ListFilter,
} from '@/apis/admin.qnaCache.api';

export function useAdminQnaCacheKpi() {
  return useQuery({
    queryKey: ['admin', 'qna-cache', 'stats', 'kpi'],
    queryFn: getQnaCacheKpi,
    staleTime: 60_000,
  });
}

export function useAdminQnaCacheDailyStats(days = 14) {
  return useQuery({
    queryKey: ['admin', 'qna-cache', 'stats', 'daily', days],
    queryFn: () => getQnaCacheDailyStats(days),
    staleTime: 60_000,
  });
}

export function useAdminQnaCacheLookups(filter: ListFilter) {
  return useQuery({
    queryKey: ['admin', 'qna-cache', 'list', filter],
    queryFn: () => listQnaCacheLookups(filter),
    staleTime: 10_000,
  });
}
