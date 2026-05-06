import api from './client';

export type LlmModule = 'QNA' | 'GUIDE' | 'EMBEDDING' | 'INGESTION' | 'ELIGIBILITY';

export interface LlmCostKpiResponse {
  todayCostUsd: number;
  thisWeekCostUsd: number;
  thisMonthCostUsd: number;
  thisMonthCallCount: number;
  usdToKrwRate: number;
  lastMonthCostUsd: number;
}

export interface LlmCostSeriesPoint {
  at: string;
  costByModule: Partial<Record<LlmModule, number>>;
}

export interface LlmCostSeriesResponse {
  range: string;
  points: LlmCostSeriesPoint[];
}

export interface LlmCostModuleDailyResponse {
  date: string;
  module: LlmModule;
  totalCostUsd: number;
  callCount: number;
}

export interface LlmCostModelSummaryResponse {
  model: string;
  callCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  totalCostUsd: number;
  costShare: number;
}

export const fetchLlmCostKpi = (): Promise<LlmCostKpiResponse> =>
  api.get('v1/admin/llm-cost/kpi').json<LlmCostKpiResponse>();

export const fetchLlmCostSeries = (range: '24h' | '7d' | '30d'): Promise<LlmCostSeriesResponse> =>
  api.get(`v1/admin/llm-cost/series?range=${range}`).json<LlmCostSeriesResponse>();

export const fetchLlmCostByModule = (range: '7d' | '30d'): Promise<LlmCostModuleDailyResponse[]> =>
  api.get(`v1/admin/llm-cost/by-module?range=${range}`).json<LlmCostModuleDailyResponse[]>();

export const fetchLlmCostByModel = (range: '7d' | '30d'): Promise<LlmCostModelSummaryResponse[]> =>
  api.get(`v1/admin/llm-cost/by-model?range=${range}`).json<LlmCostModelSummaryResponse[]>();
