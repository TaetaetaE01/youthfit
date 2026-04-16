import api from './client';
import type { ApiResponse } from '@/types/policy';

interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export async function loginWithKakao(code: string): Promise<AuthTokens> {
  const res = await api.post('auth/kakao', { json: { code } }).json<ApiResponse<AuthTokens>>();
  return res.data;
}

export async function refreshToken(refreshToken: string): Promise<AuthTokens> {
  const res = await api.post('auth/refresh', { json: { refreshToken } }).json<ApiResponse<AuthTokens>>();
  return res.data;
}

export async function logout(): Promise<void> {
  await api.post('auth/logout');
}
