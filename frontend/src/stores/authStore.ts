import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  isAuthenticated: boolean;
  login: (token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('accessToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
  login: (token: string) => {
    localStorage.setItem('accessToken', token);
    set({ accessToken: token, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem('accessToken');
    set({ accessToken: null, isAuthenticated: false });
  },
}));
