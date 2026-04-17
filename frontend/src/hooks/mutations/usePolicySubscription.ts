import { useMutation, useQueryClient } from '@tanstack/react-query';
import { subscribePolicy, unsubscribePolicy } from '@/apis/subscription.api';

export function useSubscribePolicy() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (policyId: number) => subscribePolicy(policyId),
    onSuccess: (data, policyId) => {
      queryClient.setQueryData(['policySubscription', policyId], data);
    },
  });
}

export function useUnsubscribePolicy() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (policyId: number) => unsubscribePolicy(policyId),
    onSuccess: (_data, policyId) => {
      queryClient.setQueryData(['policySubscription', policyId], {
        subscriptionId: null,
        policyId,
        subscribed: false,
        subscribedAt: null,
      });
    },
  });
}
