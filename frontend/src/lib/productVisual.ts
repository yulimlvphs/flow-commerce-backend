export type ProductVisual = { icon: string; bg: string; color: string }

const visuals: Array<ProductVisual & { keywords: string[] }> = [
  { keywords: ['티셔츠', 'tee', 't-shirt'], icon: 'ri-t-shirt-line', bg: 'from-sky-50 to-blue-100', color: 'text-sky-300' },
  { keywords: ['데님', '팬츠', 'denim'], icon: 'ri-scissors-cut-line', bg: 'from-indigo-50 to-indigo-100', color: 'text-indigo-300' },
  { keywords: ['니트', 'knit', '스웨터'], icon: 'ri-t-shirt-2-line', bg: 'from-amber-50 to-orange-100', color: 'text-amber-300' },
  { keywords: ['카고', '조거'], icon: 'ri-compass-3-line', bg: 'from-emerald-50 to-green-100', color: 'text-emerald-300' },
  { keywords: ['백', 'bag', '크로스백'], icon: 'ri-handbag-line', bg: 'from-rose-50 to-pink-100', color: 'text-rose-300' },
  { keywords: ['스니커즈', '운동화', '러닝'], icon: 'ri-footprint-line', bg: 'from-orange-50 to-red-100', color: 'text-orange-300' },
  { keywords: ['코트', 'coat', '재킷'], icon: 'ri-shirt-line', bg: 'from-gray-100 to-slate-200', color: 'text-gray-400' },
  { keywords: ['치노', 'chino', '슬랙스'], icon: 'ri-palette-line', bg: 'from-violet-50 to-purple-100', color: 'text-violet-300' },
  { keywords: ['캡', '모자', '볼캡', 'hat'], icon: 'ri-graduation-cap-line', bg: 'from-yellow-50 to-amber-100', color: 'text-yellow-400' }
]

const fallback: ProductVisual = {
  icon: 'ri-shopping-bag-line',
  bg: 'from-gray-50 to-gray-100',
  color: 'text-gray-300'
}

export function getProductVisual(name?: string): ProductVisual {
  if (!name) return fallback
  const normalized = name.toLowerCase()
  return visuals.find((visual) => visual.keywords.some((keyword) => normalized.includes(keyword))) ?? fallback
}
