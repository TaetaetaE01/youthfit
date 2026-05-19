import { useEffect, useReducer, useCallback } from 'react';
import {
  fetchIngestionKpi,
  fetchIngestionDailyStats,
  fetchIngestionSources,
  fetchIngestionStaleSources,
  searchIngestionFailures,
  retryIngestionFailure,
  type IngestionKpiResponse,
  type IngestionDailyStatsResponse,
  type IngestionSourceSummaryResponse,
  type IngestionStaleSourceResponse,
  type IngestionFailureSummaryResponse,
  type FailureSearchParams,
  type IngestionRetryResponse,
} from '@/apis/admin.ingestion.api';

interface DoneData {
  kpi: IngestionKpiResponse;
  daily: IngestionDailyStatsResponse[];
  sources: IngestionSourceSummaryResponse[];
  stale: IngestionStaleSourceResponse[];
  failures: IngestionFailureSummaryResponse[];
  totalFailures: number;
}

type State =
  | { status: 'loading' }
  | ({ status: 'done' } & DoneData)
  | { status: 'error'; error: Error };

type Action =
  | { type: 'LOAD' }
  | ({ type: 'OK' } & DoneData)
  | { type: 'ERR'; error: Error };

function reducer(_: State, action: Action): State {
  switch (action.type) {
    case 'LOAD':
      return { status: 'loading' };
    case 'OK':
      return {
        status: 'done',
        kpi: action.kpi,
        daily: action.daily,
        sources: action.sources,
        stale: action.stale,
        failures: action.failures,
        totalFailures: action.totalFailures,
      };
    case 'ERR':
      return { status: 'error', error: action.error };
  }
}

export function useAdminIngestion(searchParams: FailureSearchParams) {
  const { page, size, reason, source } = searchParams;
  const [state, dispatch] = useReducer(reducer, { status: 'loading' });

  const reload = useCallback(() => {
    dispatch({ type: 'LOAD' });
    Promise.all([
      fetchIngestionKpi(),
      fetchIngestionDailyStats(14),
      fetchIngestionSources(),
      fetchIngestionStaleSources(),
      searchIngestionFailures({ page, size, reason, source }),
    ])
      .then(([kpi, daily, sources, stale, pageResult]) => {
        dispatch({
          type: 'OK',
          kpi,
          daily,
          sources,
          stale,
          failures: pageResult.content ?? [],
          totalFailures: pageResult.totalElements ?? 0,
        });
      })
      .catch((e: unknown) =>
        dispatch({ type: 'ERR', error: e instanceof Error ? e : new Error(String(e)) }),
      );
  }, [page, size, reason, source]);

  useEffect(() => {
    reload();
  }, [reload]);

  const retry = async (id: number): Promise<IngestionRetryResponse> => {
    const result = await retryIngestionFailure(id);
    reload();
    return result;
  };

  return {
    kpi: state.status === 'done' ? state.kpi : undefined,
    daily: state.status === 'done' ? state.daily : [],
    sources: state.status === 'done' ? state.sources : [],
    stale: state.status === 'done' ? state.stale : [],
    failures: state.status === 'done' ? state.failures : [],
    totalFailures: state.status === 'done' ? state.totalFailures : 0,
    loading: state.status === 'loading',
    error: state.status === 'error' ? state.error : undefined,
    retry,
  };
}
