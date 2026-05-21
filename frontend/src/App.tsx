import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AppLayout from '@/components/layout/AppLayout';
import AdminLayout from '@/components/layout/AdminLayout';
import { RequireAdmin } from '@/components/auth/RequireAdmin';
import LandingPage from '@/pages/LandingPage';
import PolicyListPage from '@/pages/PolicyListPage';
import PolicyCalendarPage from '@/pages/PolicyCalendarPage';
import PolicyDetailPage from '@/pages/PolicyDetailPage';
import LoginPage from '@/pages/LoginPage';
import KakaoCallbackPage from '@/pages/KakaoCallbackPage';
import MyPage from '@/pages/MyPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import AdminEmailLogPage from '@/pages/admin/AdminEmailLogPage';
import AdminEmailDetailPage from '@/pages/admin/AdminEmailDetailPage';
import AdminQnaCachePage from '@/pages/admin/AdminQnaCachePage';
import AdminQnaCacheDetailPage from '@/pages/admin/AdminQnaCacheDetailPage';
import AdminLlmCostPage from '@/pages/admin/AdminLlmCostPage';
import AdminIngestionPage from '@/pages/admin/AdminIngestionPage';
import AdminIngestionFailureDetailPage from '@/pages/admin/AdminIngestionFailureDetailPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />

          <Route element={<AppLayout />}>
            <Route path="/policies" element={<PolicyListPage />} />
            <Route path="/policies/calendar" element={<PolicyCalendarPage />} />
            <Route path="/policies/:policyId" element={<PolicyDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
            <Route path="/mypage" element={<MyPage />} />
          </Route>

          {/* 어드민 — RequireAdmin → AdminLayout → 자식 페이지 */}
          <Route element={<RequireAdmin />}>
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<AdminDashboardPage />} />
              <Route path="email" element={<AdminEmailLogPage />} />
              <Route path="email/:attemptId" element={<AdminEmailDetailPage />} />
              <Route path="qna-cache" element={<AdminQnaCachePage />} />
              <Route path="qna-cache/:lookupId" element={<AdminQnaCacheDetailPage />} />
              <Route path="llm-cost" element={<AdminLlmCostPage />} />
              <Route path="ingestion" element={<AdminIngestionPage />} />
              <Route path="ingestion/failures/:id" element={<AdminIngestionFailureDetailPage />} />
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
