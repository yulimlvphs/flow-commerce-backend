import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import { useAuth } from '../context/AuthContext'
import { dealStatusLabel } from '../lib/deal'
import type { Deal } from '../types'

export function DealDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const [deal, setDeal] = useState<Deal | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    document.title = '딜 상세 | Flash Deal'
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
      <div><p className="inline-block bg-gray-100 rounded-full px-4 py-2 font-bold text-xs mb-5">{dealStatusLabel[deal.displayStatus]}</p><h1 className="text-4xl font-black">{deal.productName}</h1><p className="line-through text-gray-400 mt-6">{formatPrice(deal.originalPrice)}원</p><p className="text-4xl font-black mt-2"><span className="text-red-500 mr-3">{deal.discountRate}%</span>{formatPrice(deal.dealPrice)}<span className="text-lg">원</span></p><p className="text-gray-600 whitespace-pre-wrap mt-8">{deal.productDescription}</p>
        <dl className="mt-8 rounded-2xl bg-gray-50 p-6 grid grid-cols-[auto_1fr] gap-x-6 gap-y-4 text-sm"><dt>남은 수량</dt><dd>{deal.remainingStock}개</dd><dt>1인 구매 한도</dt><dd>{deal.maxPurchasePerUser}개</dd><dt>오픈 시각 (KST)</dt><dd>{deal.openAt.replace('T', ' ')}</dd><dt>종료 시각 (KST)</dt><dd>{deal.closeAt.replace('T', ' ')}</dd><dt>결제 대기 시간</dt><dd>{deal.holdDurationMinutes}분</dd></dl><div className="mt-6 flex flex-wrap gap-3"><button onClick={() => setRetry(value => value + 1)} className="border rounded-xl px-5 py-3 text-sm">상태 새로고침</button>{(user?.role === 'SELLER' || user?.role === 'ADMIN') && <Link to="/seller/deals" className="bg-black text-white rounded-xl px-5 py-3 text-sm">딜 관리로 이동</Link>}</div>
      </div>
    </div>
  </div>
}
