import ky from 'ky';
import { useAuthStore } from '@/stores/authStore';

const api = ky.create({
  prefix: '/api',
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const token = useAuthStore.getState().accessToken;
        if (token) {
          request.headers.set('Authorization', `Bearer ${token}`);
        }
      },
    ],
    afterResponse: [
      async ({ response }) => {
        if (response.status === 401) {
          useAuthStore.getState().logout();
        }
      },
    ],
  },
});

export default api;
