import { Outlet } from 'react-router-dom';
import { useState } from 'react';
import Drawer from '../components/admin/Drawer';
import Sidebar from '../components/admin/Sidebar';
import TopBar from '../components/admin/TopBar';

export default function AdminLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <div className="min-h-screen bg-bg lg:flex">
      <aside className="hidden w-72 shrink-0 bg-gradient-to-b from-primary-dark via-primary-dark to-primary p-5 lg:sticky lg:top-0 lg:block lg:h-screen lg:overflow-y-auto">
        <Sidebar />
      </aside>
      <div className="min-w-0 flex-1">
        <TopBar onOpenNavigation={() => setDrawerOpen(true)} />
        <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
        <main className="page-enter mx-auto w-full max-w-screen-2xl p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
