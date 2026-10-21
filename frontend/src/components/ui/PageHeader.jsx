export default function PageHeader({ title, description, actions, children }) {
  return (
    <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-[28px] font-semibold leading-tight tracking-tight text-app-text sm:text-[32px]">{title}</h1>
        {description && <p className="mt-2 max-w-3xl text-sm leading-relaxed text-muted sm:text-base">{description}</p>}
        {children}
      </div>
      {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
    </header>
  );
}
