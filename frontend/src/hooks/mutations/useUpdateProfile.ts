import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProfile } from '@/apis/user.api';
import type { UpdateProfileRequest } from '@/types/policy';

export function useUpdateProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => updateProfile(data),
    onSuccess: (updatedProfile) => {
      queryClient.setQueryData(['profile'], updatedProfile);
    },
  });
}
