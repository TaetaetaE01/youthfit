import { useQuery } from '@tanstack/react-query';
import { fetchNotificationSettings } from '@/apis/user.api';

export function useNotificationSettings() {
  return useQuery({
    queryKey: ['notificationSettings'],
    queryFn: fetchNotificationSettings,
  });
}
