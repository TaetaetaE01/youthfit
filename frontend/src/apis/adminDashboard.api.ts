import api from './client';
import type { DashboardOverview } from '@/types/adminDashboard';

interface ApiEnvelope<T> { data: T }

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const res = await api.get('v1/admin/dashboard/overview').json<ApiEnvelope<DashboardOverview>>();
  return res.data;
}
