import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateNotificationSettings } from '@/apis/user.api';
import type { NotificationSettings } from '@/types/policy';

export function useUpdateNotificationSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: NotificationSettings) => updateNotificationSettings(data),
    onSuccess: (updatedSettings) => {
      queryClient.setQueryData(['notificationSettings'], updatedSettings);
    },
  });
}
