export function Loading({ className = 'py-24' }: { className?: string }) {
  return <div className={`text-center ${className}`}><div className="spinner-dark mx-auto" /></div>
}
