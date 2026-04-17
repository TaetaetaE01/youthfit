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
      async ({ request, response }) => {
        if (response.status === 401 || response.status === 403) {
          const hadToken = request.headers.get('Authorization');
          if (hadToken) {
            useAuthStore.getState().logout();
          }
        }
      },
    ],
  },
});

export default api;
