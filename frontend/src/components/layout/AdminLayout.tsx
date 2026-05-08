import { Outlet } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import AdminHeader from './AdminHeader';

export default function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <AdminSidebar />
      <div className="relative flex min-w-0 flex-1 flex-col">
        <AdminHeader />
        <main className="relative flex-1 overflow-x-hidden">
          {/* subtle dot pattern background */}
          <div
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_1px_1px,rgba(15,23,42,0.045)_1px,transparent_0)] [background-size:22px_22px] [mask-image:linear-gradient(to_bottom,black,transparent_85%)]"
            aria-hidden
          />
          {/* top accent gradient */}
          <div
            className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-white/60 to-transparent"
            aria-hidden
          />
          <div className="relative mx-auto w-full max-w-[1440px] p-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
