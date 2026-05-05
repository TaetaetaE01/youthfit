import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { RequireAdmin } from '../RequireAdmin';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>LOGIN_PAGE</div>} />
        <Route path="/" element={<div>HOME_PAGE</div>} />
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<div>ADMIN_AREA</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAdmin', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ accessToken: null, isAuthenticated: false, role: null });
  });

  it('비인증 → 로그인 페이지로 리다이렉트', () => {
    renderAt('/admin');
    expect(screen.getByText('LOGIN_PAGE')).toBeInTheDocument();
  });

  it('role=USER → 홈으로 리다이렉트', () => {
    useAuthStore.setState({ accessToken: 't', isAuthenticated: true, role: 'USER' });
    renderAt('/admin');
    expect(screen.getByText('HOME_PAGE')).toBeInTheDocument();
  });

  it('role=ADMIN → 어드민 영역 렌더', () => {
    useAuthStore.setState({ accessToken: 't', isAuthenticated: true, role: 'ADMIN' });
    renderAt('/admin');
    expect(screen.getByText('ADMIN_AREA')).toBeInTheDocument();
  });
});
