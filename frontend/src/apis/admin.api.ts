import api from './client';

export interface AdminPingResponse {
  message: string;
  serverTime: string;
}

interface ApiEnvelope<T> { data: T }

export async function pingAdmin(): Promise<AdminPingResponse> {
  const res = await api.get('v1/admin/ping').json<ApiEnvelope<AdminPingResponse>>();
  return res.data;
}
