import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AdminLayout from '../AdminLayout';

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<div>DASHBOARD</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminLayout', () => {
  it('사이드바에 4개 메뉴를 노출한다', () => {
    renderLayout();
    expect(screen.getByText('이메일 발송')).toBeInTheDocument();
    expect(screen.getByText('Q&A 캐시 로그')).toBeInTheDocument();
    expect(screen.getByText('LLM 비용')).toBeInTheDocument();
    expect(screen.getByText('Ingestion 헬스')).toBeInTheDocument();
  });

  it('자식 라우트(Outlet) 컨텐츠를 렌더한다', () => {
    renderLayout();
    expect(screen.getByText('DASHBOARD')).toBeInTheDocument();
  });
});
