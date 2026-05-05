import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '@/stores/authStore';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ accessToken: null, isAuthenticated: false, role: null });
  });

  it('login(token, role) 호출 시 accessToken과 role을 저장한다', () => {
    useAuthStore.getState().login('tok-123', 'ADMIN');

    const s = useAuthStore.getState();
    expect(s.accessToken).toBe('tok-123');
    expect(s.isAuthenticated).toBe(true);
    expect(s.role).toBe('ADMIN');
  });

  it('logout 시 role도 초기화된다', () => {
    useAuthStore.getState().login('tok-123', 'ADMIN');
    useAuthStore.getState().logout();

    const s = useAuthStore.getState();
    expect(s.accessToken).toBeNull();
    expect(s.isAuthenticated).toBe(false);
    expect(s.role).toBeNull();
  });
});
