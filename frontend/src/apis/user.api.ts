import api from './client';
import type { ApiResponse, UserProfile, UpdateProfileRequest, NotificationSettings } from '@/types/policy';

export async function fetchProfile(): Promise<UserProfile> {
  const res = await api.get('v1/users/me').json<ApiResponse<UserProfile>>();
  return res.data;
}

export async function updateProfile(data: UpdateProfileRequest): Promise<UserProfile> {
  const res = await api.patch('v1/users/me', { json: data }).json<ApiResponse<UserProfile>>();
  return res.data;
}

export async function fetchNotificationSettings(): Promise<NotificationSettings> {
  const res = await api.get('v1/notifications/settings').json<ApiResponse<NotificationSettings>>();
  return res.data;
}

export async function updateNotificationSettings(data: NotificationSettings): Promise<NotificationSettings> {
  const res = await api.put('v1/notifications/settings', { json: data }).json<ApiResponse<NotificationSettings>>();
  return res.data;
}
