export default function Card({ children, className = '', as: Component = 'section', ...props }) {
  return (
    <Component
      {...props}
      className={`rounded-2xl border border-border bg-card p-5 shadow-card transition duration-150 hover:-translate-y-0.5 hover:shadow-card-hover sm:p-6 ${className}`}
    >
      {children}
    </Component>
  );
}
