import { getProductVisual } from '../lib/productVisual'

export function ProductVisual({ name, className = '', iconClassName = 'text-5xl' }: { name: string; className?: string; iconClassName?: string }) {
  const visual = getProductVisual(name)
  return (
    <div className={`bg-gradient-to-br ${visual.bg} flex items-center justify-center ${className}`}>
      <i className={`${visual.icon} ${iconClassName} ${visual.color}`} aria-hidden="true" />
    </div>
  )
}
