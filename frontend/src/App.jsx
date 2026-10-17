/**
 * App.jsx
 *
 * Root application component.
 * Phase 1: minimal placeholder to verify the build.
 * Full routing and AuthContext will be wired in Phase 2.
 */
function App() {
  return (
    <div className="min-h-screen bg-bg flex items-center justify-center">
      <div className="bg-card rounded-2xl shadow-card p-8 max-w-sm w-full text-center">
        <h1 className="text-2xl font-bold text-primary mb-2">
          Nexus Quiz Portal
        </h1>
        <p className="text-app-text text-sm">
          Nexus Ranaji English Medium School
        </p>
        <p className="text-gray-400 text-xs mt-4">
          Phase 1 scaffold – build verification placeholder
        </p>
      </div>
    </div>
  );
}

export default App;
