import { LoaderCircle } from 'lucide-react';

export default function LoadingScreen() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="flex items-center gap-3 rounded-2xl border border-border bg-card px-6 py-5 text-app-text shadow-card">
        <LoaderCircle className="h-6 w-6 animate-spin text-primary" aria-hidden="true" />
        <span className="font-medium">Checking your session…</span>
      </div>
    </main>
  );
}
