export type DashboardSeverity = 'HIGH' | 'MEDIUM';
export type AreaStatus = 'OK' | 'WARN' | 'CRITICAL';

export interface DashboardActionItem {
  code: string;
  severity: DashboardSeverity;
  title: string;
  detail: string | null;
  deeplink: string;
  detectedAt: string;
}

export interface DashboardAreaStatus {
  key: string;
  label: string;
  status: AreaStatus;
  summary: string;
  sparkline: number[];
  deeplink: string;
}

export interface DashboardOverview {
  generatedAt: string;
  actionItems: DashboardActionItem[];
  areas: DashboardAreaStatus[];
}
