import { create } from 'zustand';

type Role = 'USER' | 'ADMIN';

interface AuthState {
  accessToken: string | null;
  isAuthenticated: boolean;
  role: Role | null;
  login: (token: string, role?: Role | null) => void;
  setRole: (role: Role | null) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('accessToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
  role: (localStorage.getItem('role') as Role | null) ?? null,
  login: (token, role = null) => {
    localStorage.setItem('accessToken', token);
    if (role) localStorage.setItem('role', role);
    else localStorage.removeItem('role');
    set({ accessToken: token, isAuthenticated: true, role });
  },
  setRole: (role) => {
    if (role) localStorage.setItem('role', role);
    else localStorage.removeItem('role');
    set({ role });
  },
  logout: () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('role');
    set({ accessToken: null, isAuthenticated: false, role: null });
  },
}));
