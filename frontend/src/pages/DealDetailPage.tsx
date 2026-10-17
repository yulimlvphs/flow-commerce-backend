import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import { dealStatusLabel } from '../lib/deal'
import type { Deal } from '../types'

export function DealDetailPage() {
  const { id } = useParams()
  const [deal, setDeal] = useState<Deal | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    document.title = '딜 상세 | SpartaFashion'
    setLoading(true); setError(''); setDeal(null)
    apiFetch<Deal>(`/api/deals/${id}`).then(result => { if (active) setDeal(result) })
      .catch((e: Error) => { if (active) setError(e.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, retry])
  if (loading) return <Loading />
  if (!deal) return <div className="text-center py-24"><p role="alert">{error || '딜을 찾을 수 없습니다.'}</p><button onClick={() => setRetry(retry + 1)} className="underline mt-4 mr-5">다시 시도</button><Link to="/">딜 목록으로</Link></div>
  return <div className="max-w-7xl mx-auto px-6 py-12">
    <Link to="/" className="text-sm text-gray-500">← 딜 목록</Link>
    <div className="grid lg:grid-cols-2 gap-12 mt-8">
      {deal.thumbnailUrl ? <img src={deal.thumbnailUrl} alt={deal.productName} className="aspect-square object-cover w-full rounded-lg" /> : <ProductVisual name={deal.productName} className="aspect-square rounded-lg" iconClassName="text-8xl" />}
      <div><p className="font-bold text-sm mb-4">{dealStatusLabel[deal.displayStatus]}</p><h1 className="text-4xl font-black">{deal.productName}</h1><p className="line-through text-gray-400 mt-6">{formatPrice(deal.originalPrice)}원</p><p className="text-3xl font-bold mt-1">{formatPrice(deal.dealPrice)}원</p><p className="text-gray-600 whitespace-pre-wrap mt-8">{deal.productDescription}</p>
        <dl className="mt-8 grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 text-sm"><dt>남은 수량</dt><dd>{deal.remainingStock}개</dd><dt>1인 구매 한도</dt><dd>{deal.maxPurchasePerUser}개</dd><dt>오픈 시각</dt><dd>{deal.openAt.replace('T', ' ')}</dd><dt>종료 시각</dt><dd>{deal.closeAt.replace('T', ' ')}</dd></dl>
      </div>
    </div>
  </div>
}
