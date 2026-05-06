import { useEffect, useReducer } from 'react';
import {
  fetchLlmCostByModel,
  fetchLlmCostByModule,
  fetchLlmCostKpi,
  fetchLlmCostSeries,
  type LlmCostKpiResponse,
  type LlmCostModelSummaryResponse,
  type LlmCostModuleDailyResponse,
  type LlmCostSeriesResponse,
} from '@/apis/admin.llmCost.api';

export type Range = '24h' | '7d' | '30d';

type State =
  | { status: 'loading' }
  | {
      status: 'done';
      kpi: LlmCostKpiResponse;
      series: LlmCostSeriesResponse;
      byModule: LlmCostModuleDailyResponse[];
      byModel: LlmCostModelSummaryResponse[];
    }
  | { status: 'error'; error: Error };

type Action =
  | { type: 'LOAD' }
  | {
      type: 'OK';
      kpi: LlmCostKpiResponse;
      series: LlmCostSeriesResponse;
      byModule: LlmCostModuleDailyResponse[];
      byModel: LlmCostModelSummaryResponse[];
    }
  | { type: 'ERR'; error: Error };

function reducer(_: State, action: Action): State {
  switch (action.type) {
    case 'LOAD':
      return { status: 'loading' };
    case 'OK':
      return {
        status: 'done',
        kpi: action.kpi,
        series: action.series,
        byModule: action.byModule,
        byModel: action.byModel,
      };
    case 'ERR':
      return { status: 'error', error: action.error };
  }
}

export function useAdminLlmCost(range: Range) {
  const [state, dispatch] = useReducer(reducer, { status: 'loading' });

  useEffect(() => {
    dispatch({ type: 'LOAD' });
    const dailyRange: '7d' | '30d' = range === '24h' ? '7d' : range;
    Promise.all([
      fetchLlmCostKpi(),
      fetchLlmCostSeries(range),
      fetchLlmCostByModule(dailyRange),
      fetchLlmCostByModel(dailyRange),
    ])
      .then(([kpi, series, byModule, byModel]) =>
        dispatch({ type: 'OK', kpi, series, byModule, byModel }),
      )
      .catch((e: unknown) =>
        dispatch({ type: 'ERR', error: e instanceof Error ? e : new Error(String(e)) }),
      );
  }, [range]);

  return {
    kpi: state.status === 'done' ? state.kpi : undefined,
    series: state.status === 'done' ? state.series : undefined,
    byModule: state.status === 'done' ? state.byModule : [],
    byModel: state.status === 'done' ? state.byModel : [],
    loading: state.status === 'loading',
    error: state.status === 'error' ? state.error : undefined,
  };
}
