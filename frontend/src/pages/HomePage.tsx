import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProductVisual } from '../components/ProductVisual'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import { dealStatusLabel } from '../lib/deal'
import type { Deal, PageResponse, Product } from '../types'

const filters = [{ value: '', label: '전체' }, { value: 'ON_SALE', label: '판매 중' }, { value: 'UPCOMING', label: '오픈 예정' }, { value: 'SOLD_OUT', label: '품절' }]
export function HomePage({ management = false }: { management?: boolean }) {
  const { user } = useAuth()
  const { showConfirm, showToast } = useUi()
  const [deals, setDeals] = useState<Deal[]>([])
  const [owned, setOwned] = useState<number[]>([])
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [ownershipError, setOwnershipError] = useState('')
  const [retry, setRetry] = useState(0)
  const [busy, setBusy] = useState(false)
  const [receivedAt, setReceivedAt] = useState(0)
  const [now, setNow] = useState(Date.now())
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [])
  useEffect(() => {
    const controller = new AbortController()
    document.title = `${management ? '딜 관리' : '타임딜'} | Flash Deal`
    setLoading(true); setError(''); setOwned([]); setOwnershipError('')
    void (async () => {
      try {
        const result = await apiFetch<PageResponse<Deal>>(`/api/deals?page=${page}&size=20${status ? `&status=${status}` : ''}`, { signal: controller.signal })
        if (controller.signal.aborted) return
        if (page > 0 && page >= result.totalPages) { setPage(Math.max(0, result.totalPages - 1)); return }
        setDeals(result.content); setTotalPages(result.totalPages); setTotal(result.totalElements); setReceivedAt(Date.now())
        if (management && user?.role === 'SELLER') {
          const ids = [...new Set(result.content.map(deal => deal.productId))]
          const products = await Promise.allSettled(ids.map(id => apiFetch<Product>(`/api/products/${id}`, { signal: controller.signal })))
          if (controller.signal.aborted) return
          setOwned(products.flatMap(result => result.status === 'fulfilled' && result.value.sellerId === user.id ? [result.value.id] : []))
          if (products.some(result => result.status === 'rejected')) setOwnershipError('일부 상품의 관리 권한을 확인하지 못했습니다. 새로고침 후 다시 확인하세요.')
        }
      } catch (e) { if (!controller.signal.aborted) setError((e as Error).message) }
      finally { if (!controller.signal.aborted) setLoading(false) }
    })()
    return () => controller.abort()
  }, [page, retry, status, management, user?.id, user?.role])
  const cancel = (deal: Deal) => showConfirm(`‘${deal.productName}’ 딜을 취소할까요? 취소하면 판매가 종료되며 다시 열 수 없습니다.`, () => {
    setBusy(true)
    void apiFetch<void>(`/api/deals/${deal.dealId}`, { method: 'DELETE' })
      .then(() => { showToast('딜을 취소했습니다.'); setRetry(value => value + 1) })
      .catch((e: Error) => showToast(e.message, 'danger'))
      .finally(() => setBusy(false))
  })
  return <>
    <section className={management ? 'bg-gray-50 border-b' : 'bg-black text-white hero-gradient-animate'}><div className="max-w-7xl mx-auto px-6 py-12 sm:py-16"><p className="text-xs tracking-[0.2em] text-gray-400 mb-4">{management ? 'SELLER CENTER · TIME DEAL' : 'Flash Deal · TIME DEAL'}</p><div className="flex flex-wrap justify-between gap-6 items-center"><div><h1 className="text-3xl sm:text-5xl font-black tracking-tight">{management ? '타임딜 관리' : '지금 만나는 타임딜'}</h1><p className={`mt-5 text-sm ${management ? 'text-gray-500' : 'text-gray-300'}`}>{management ? '특별한 가격, 정확한 일정. 다음 타임딜을 준비하세요.' : '한정된 시간, 특별한 가격으로 만나는 스타일.'}</p></div>{management && <Link to="/seller/deals/new" className="rounded-xl bg-black text-white px-6 py-4 text-sm font-bold">+ 새 딜 등록</Link>}</div></div></section>
    <section className="max-w-7xl mx-auto px-4 sm:px-6 py-10">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-6"><div className="flex flex-wrap gap-2" aria-label="딜 상태 필터">{filters.map(filter => <button key={filter.value} aria-pressed={status === filter.value} onClick={() => { setStatus(filter.value); setPage(0) }} className={`rounded-full px-5 py-2.5 text-sm font-medium transition-colors ${status === filter.value ? 'bg-black text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>{filter.label}</button>)}</div><button disabled={loading || busy} onClick={() => setRetry(value => value + 1)} className="text-sm text-gray-500 disabled:opacity-40">↻ 새로고침</button></div>
      <p className="text-xs text-gray-500 mb-6">{loading ? '목록 확인 중…' : `전체 ${total}개`}{management && ` · 종료·취소된 딜은 제외됩니다. ${user?.role === 'ADMIN' ? '모든 상품' : '본인 상품'}의 오픈 예정 딜을 관리할 수 있습니다.`}</p>
      {ownershipError && <p role="alert" className="text-sm text-amber-700 mb-5">{ownershipError}</p>}
      {error && <div role="alert" className="rounded-2xl bg-red-50 text-red-700 p-6">{error} <button onClick={() => setRetry(value => value + 1)} className="underline ml-3">다시 시도</button></div>}
      {loading ? <div role="status" aria-label="딜을 불러오는 중" className="grid grid-cols-2 lg:grid-cols-4 gap-5">{[1, 2, 3, 4].map(key => <div key={key} className="h-80 rounded-2xl bg-gray-100 animate-pulse" />)}</div> : !error && <>
        {deals.length === 0 && <div className="text-center rounded-2xl border border-dashed py-20"><p className="text-lg font-bold">{status ? '해당 상태의 딜이 없습니다' : '아직 등록된 딜이 없습니다'}</p><p className="text-sm text-gray-500 mt-3">{management ? '첫 타임딜을 등록하고 판매를 준비하세요.' : '새로운 타임딜이 곧 찾아옵니다.'}</p></div>}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">{deals.map(deal => {
          const editable = management && (user?.role === 'ADMIN' || owned.includes(deal.productId)) && deal.displayStatus === 'UPCOMING' && Date.parse(`${deal.openAt}+09:00`) > Date.parse(`${deal.serverNow}+09:00`) + Math.max(0, now - receivedAt)
          return <article key={deal.dealId} className="group border border-gray-100 rounded-2xl overflow-hidden bg-white transition-shadow hover:shadow-lg">
            <Link to={`/deals/${deal.dealId}`} className="block"><div className="relative overflow-hidden bg-gray-50">{deal.thumbnailUrl ? <img src={deal.thumbnailUrl} alt={deal.productName} loading="lazy" className="aspect-[4/3] w-full object-cover transition-transform duration-500 group-hover:scale-105" /> : <ProductVisual name={deal.productName} className="aspect-[4/3]" />}<span className={`absolute top-3 left-3 rounded-full px-3 py-1.5 text-xs font-bold ${deal.displayStatus === 'ON_SALE' ? 'bg-black text-white' : 'bg-white text-gray-700'}`}>{dealStatusLabel[deal.displayStatus]}</span></div>
              <div className="p-5"><h2 className="font-bold truncate">{deal.productName}</h2><p className="text-xs text-gray-400 line-through mt-4">{formatPrice(deal.originalPrice)}원</p><p className="text-xl font-black mt-1"><span className="text-red-500 mr-2">{deal.discountRate}%</span>{formatPrice(deal.dealPrice)}<span className="text-sm font-medium">원</span></p><div className="mt-5 h-1 rounded-full bg-gray-100 overflow-hidden"><div className="h-full bg-gray-800" style={{ width: `${Math.min(100, Math.max(0, deal.remainingStock / deal.totalStock * 100))}%` }} /></div><p className="text-xs text-gray-500 mt-2">남은 수량 {deal.remainingStock}개 · 1인 {deal.maxPurchasePerUser}개</p><p className="text-xs text-gray-400 mt-4">{deal.displayStatus === 'UPCOMING' ? `오픈 ${deal.openAt.replace('T', ' ').slice(0, 16)}` : `종료 ${deal.closeAt.replace('T', ' ').slice(0, 16)}`} KST</p></div>
            </Link>
            {editable && <div className="flex gap-2 px-5 pb-5"><Link to={`/seller/deals/${deal.dealId}/edit`} className={`flex-1 text-center border rounded-lg py-2 text-sm ${busy ? 'pointer-events-none opacity-40' : ''}`}>수정</Link><button disabled={busy} onClick={() => cancel(deal)} className="flex-1 text-red-600 bg-red-50 rounded-lg py-2 text-sm disabled:opacity-40">딜 취소</button></div>}
          </article>
        })}</div>
        {totalPages > 0 && <nav aria-label="딜 페이지" className="flex justify-center items-center gap-6 mt-10"><button disabled={page === 0 || busy} onClick={() => setPage(page - 1)} className="border rounded-lg px-4 py-2 text-sm disabled:opacity-30">이전</button><span className="text-sm tabular-nums">{page + 1} / {totalPages}</span><button disabled={page + 1 >= totalPages || busy} onClick={() => setPage(page + 1)} className="border rounded-lg px-4 py-2 text-sm disabled:opacity-30">다음</button></nav>}
      </>}
    </section>
  </>
}
