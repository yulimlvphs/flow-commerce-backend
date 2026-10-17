import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProductVisual } from '../components/ProductVisual'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import { dealStatusLabel } from '../lib/deal'
import type { Deal, PageResponse } from '../types'

export function HomePage() {
  const [deals, setDeals] = useState<Deal[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    document.title = '타임딜 | SpartaFashion'
    setLoading(true); setError('')
    apiFetch<PageResponse<Deal>>(`/api/deals?page=${page}&size=20`).then(result => {
      if (!active) return
      setDeals(result.content); setTotalPages(result.totalPages); setTotal(result.totalElements)
    }).catch((e: Error) => { if (active) setError(e.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, retry])
  return <>
    <section className="bg-black text-white hero-gradient-animate"><div className="max-w-7xl mx-auto px-6 py-16"><p className="text-sm text-gray-400 mb-4">SPARTA FASHION · TIME DEAL</p><h1 className="text-4xl md:text-6xl font-black tracking-tight">지금 만나는 타임딜</h1><p className="text-gray-300 mt-5">딜 가격과 판매 일정, 남은 수량을 확인하세요.</p></div></section>
    <section className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
      <h2 className="text-2xl font-black">판매 딜</h2><p className="text-sm text-gray-500 mt-2 mb-8">총 {total}개</p>
      {error && <div role="alert" className="text-red-600 py-8">딜을 불러오지 못했습니다: {error} <button onClick={() => setRetry(retry + 1)} className="underline">다시 시도</button></div>}
      {loading ? <p role="status">딜을 불러오는 중…</p> : !error && <>
        {deals.length === 0 && <p className="text-center text-gray-500 py-16">등록된 딜이 없습니다.</p>}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">{deals.map(deal => <article key={deal.dealId} className="border rounded-2xl overflow-hidden">
          <Link to={`/deals/${deal.dealId}`}>
            {deal.thumbnailUrl ? <img src={deal.thumbnailUrl} alt={deal.productName} className="aspect-[3/4] w-full object-cover" /> : <ProductVisual name={deal.productName} className="aspect-[3/4]" />}
            <div className="p-4"><span className="text-xs font-bold">{dealStatusLabel[deal.displayStatus]}</span><h3 className="font-bold mt-2">{deal.productName}</h3><p className="text-xs text-gray-400 line-through mt-3">{formatPrice(deal.originalPrice)}원</p><p className="text-lg font-black">{formatPrice(deal.dealPrice)}원</p><p className="text-xs text-gray-500 mt-2">남은 수량 {deal.remainingStock}개</p></div>
          </Link>
        </article>)}</div>
        <div className="flex justify-center gap-5 mt-10"><button disabled={page === 0} onClick={() => setPage(page - 1)} className="disabled:opacity-30">이전</button><span>{totalPages ? page + 1 : 0} / {totalPages}</span><button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} className="disabled:opacity-30">다음</button></div>
      </>}
    </section>
  </>
}
