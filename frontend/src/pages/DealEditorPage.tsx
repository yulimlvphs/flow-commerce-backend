import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { Deal, Product, PageResponse } from '../types'

const initial = { productId: '', dealPrice: '', totalStock: '100', maxPurchasePerUser: '1', openAt: '', closeAt: '', holdDurationMinutes: '10' }
const input = 'mt-2 block w-full rounded-xl border border-gray-200 bg-white px-4 py-3 outline-none focus:border-black focus:ring-1 focus:ring-black disabled:bg-gray-100'

export function DealEditorPage() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const preselectedProductId = params.get('productId') ?? ''
  const { user } = useAuth()
  const { showToast } = useUi()
  const navigate = useNavigate()
  const [form, setForm] = useState({ ...initial, productId: params.get('productId') ?? '' })
  const [products, setProducts] = useState<Product[]>([])
  const [deal, setDeal] = useState<Deal | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [retry, setRetry] = useState(0)
  const [receivedAt, setReceivedAt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    setLoading(true); setLoadError(''); setError('')
    if (!id) { setDeal(null); setForm({ ...initial, productId: preselectedProductId }) }
    document.title = `${id ? '딜 수정' : '새 딜 등록'} | Flash Deal`
    void (async () => {
      try {
        if (id) {
          const result = await apiFetch<Deal>(`/api/deals/${id}`, options)
          if (controller.signal.aborted) return
          setDeal(result); setReceivedAt(Date.now())
          setForm({ productId: String(result.productId), dealPrice: String(result.dealPrice), totalStock: String(result.totalStock), maxPurchasePerUser: String(result.maxPurchasePerUser), openAt: result.openAt.slice(0, 19), closeAt: result.closeAt.slice(0, 19), holdDurationMinutes: String(result.holdDurationMinutes) })
        }
        if (user?.role === 'SELLER') {
          const all: Product[] = []
          let page = 0, pages = 1
          while (page < pages) {
            const result = await apiFetch<PageResponse<Product>>(`/api/products?page=${page}&size=50&sort=id,asc`, options)
            all.push(...result.content.filter(product => product.sellerId === user.id))
            pages = result.totalPages; page++
          }
          if (!controller.signal.aborted) setProducts(all)
        }
      } catch (e) { if (!controller.signal.aborted) setLoadError((e as Error).message) }
      finally { if (!controller.signal.aborted) setLoading(false) }
    })()
    return () => controller.abort()
  }, [id, user?.id, user?.role, retry, preselectedProductId])
  const selected = products.find(product => product.id === Number(form.productId))
  const price = selected?.originalPrice ?? deal?.originalPrice
  const allowed = !id || (deal?.displayStatus === 'UPCOMING' && (user?.role === 'ADMIN' || !!selected))
  const change = (key: keyof typeof initial, value: string) => setForm(current => ({ ...current, [key]: value }))
  const save = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    const open = Date.parse(`${form.openAt}+09:00`), close = Date.parse(`${form.closeAt}+09:00`)
    const now = deal ? Date.parse(`${deal.serverNow}+09:00`) + Date.now() - receivedAt : Date.now()
    const payload = { dealPrice: Number(form.dealPrice), totalStock: Number(form.totalStock), maxPurchasePerUser: Number(form.maxPurchasePerUser), openAt: form.openAt, closeAt: form.closeAt, holdDurationMinutes: Number(form.holdDurationMinutes) }
    if (!Number.isSafeInteger(Number(form.productId)) || Number(form.productId) < 1) return setError('올바른 상품 번호를 입력하세요.')
    if (!Number.isFinite(payload.dealPrice) || payload.dealPrice <= 0 || (price !== undefined && payload.dealPrice >= price)) return setError('딜 가격은 0보다 크고 상품 정가보다 낮아야 합니다.')
    if (![payload.totalStock, payload.maxPurchasePerUser, payload.holdDurationMinutes].every(value => Number.isSafeInteger(value) && value >= 1 && value <= 2147483647) || payload.maxPurchasePerUser > payload.totalStock || payload.holdDurationMinutes > 30) return setError('재고와 구매 한도를 확인하세요. 결제 대기 시간은 1~30분입니다.')
    if (!Number.isFinite(open) || !Number.isFinite(close) || open <= now || close <= open) return setError('오픈 시각은 현재 이후, 종료 시각은 오픈 이후로 입력하세요. (한국 시간)')
    setBusy(true)
    try {
      const saved = await apiFetch<Deal>(id ? `/api/deals/${id}` : '/api/deals', { method: id ? 'PATCH' : 'POST', body: JSON.stringify(id ? payload : { ...payload, productId: Number(form.productId) }) })
      showToast(id ? '딜을 수정했습니다.' : '새로운 딜을 등록했습니다.')
      navigate(`/deals/${saved.dealId}`)
    } catch (e) { setError((e as Error).message) }
    finally { setBusy(false) }
  }
  return <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10 sm:py-14">
    <Link to="/seller/deals" className="text-sm text-gray-500 hover:text-black">← 딜 관리</Link>
    <p className="text-xs tracking-widest text-gray-400 mt-8 mb-3">TIME DEAL STUDIO</p>
    <h1 className="text-3xl font-black tracking-tight">{id ? '딜 정보 수정' : '새로운 타임딜'}</h1>
    <p className="text-gray-500 mt-3 mb-8">상품의 특별한 가격과 판매 일정을 설정하세요.</p>
    {loading ? <p role="status" className="py-16">상품 정보를 불러오는 중…</p> : loadError ? <div role="alert">{loadError} <button onClick={() => setRetry(retry + 1)} className="underline">다시 시도</button></div> : <form onSubmit={save} className="grid lg:grid-cols-[1fr_280px] gap-8 items-start">
      <fieldset disabled={busy || !allowed} className="space-y-6 disabled:opacity-60">
        {!allowed && <p role="alert" className="rounded-xl bg-amber-50 p-4 text-amber-800">본인 상품의 오픈 예정 딜만 수정할 수 있습니다. 관리자는 모든 상품의 오픈 예정 딜을 수정할 수 있습니다.</p>}
        <section className="rounded-2xl border p-6 space-y-5"><h2 className="font-bold text-lg">01 · 상품과 가격</h2>
          <label className="block text-sm font-medium">판매 상품{id ? <input className={input} disabled value={deal?.productName ?? ''} /> : user?.role === 'ADMIN' ? <input className={input} type="number" min="1" step="1" required value={form.productId} onChange={e => change('productId', e.target.value)} placeholder="상품 번호 입력" /> : <select required className={input} value={form.productId} onChange={e => change('productId', e.target.value)}><option value="">내 상품을 선택하세요</option>{products.map(product => <option key={product.id} value={product.id}>{product.name} · {formatPrice(product.originalPrice)}원</option>)}</select>}</label>
          {!id && user?.role === 'SELLER' && products.length === 0 && <p className="text-sm text-gray-500">등록된 상품이 없습니다. <Link to="/seller/products" className="underline">상품 등록하기</Link></p>}
          <label className="block text-sm font-medium">딜 가격 (원)<input className={input} required type="number" min="0.01" step="0.01" value={form.dealPrice} onChange={e => change('dealPrice', e.target.value)} /></label>
          <p className="text-xs text-gray-500">{price !== undefined ? `정가 ${formatPrice(price)}원보다 낮은 가격을 입력하세요.` : '선택한 상품의 정가보다 낮은 가격으로 설정하세요.'}</p>
        </section>
        <section className="rounded-2xl border p-6 space-y-5"><h2 className="font-bold text-lg">02 · 판매 일정</h2><p className="text-xs text-gray-500">모든 일정은 한국 시간(KST) 기준입니다.</p><div className="grid sm:grid-cols-2 gap-4">{(['openAt', 'closeAt'] as const).map(key => <label key={key} className="text-sm font-medium min-w-0">{key === 'openAt' ? '오픈 시각' : '종료 시각'}<input className={input} type="datetime-local" step="1" required value={form[key]} onChange={e => change(key, e.target.value)} /></label>)}</div></section>
        <section className="rounded-2xl border p-6 space-y-5"><h2 className="font-bold text-lg">03 · 수량과 구매 조건</h2><div className="grid sm:grid-cols-2 gap-4">{([{ key: 'totalStock', label: '총 판매 수량' }, { key: 'maxPurchasePerUser', label: '1인 구매 한도' }, { key: 'holdDurationMinutes', label: '결제 대기 시간 (분)' }] as const).map(({ key, label }) => <label key={key} className="text-sm font-medium">{label}<input className={input} required type="number" min="1" step="1" max={key === 'holdDurationMinutes' ? 30 : key === 'maxPurchasePerUser' ? Number(form.totalStock) || 1 : 2147483647} value={form[key]} onChange={e => change(key, e.target.value)} /></label>)}</div><p className="text-xs text-gray-500">결제 대기 중 재고를 확보하는 시간입니다. 1~30분으로 설정하세요.</p></section>
      </fieldset>
      <aside className="rounded-2xl bg-gray-50 p-6 lg:sticky lg:top-24"><p className="text-xs tracking-widest text-gray-500">DEAL PREVIEW</p><h2 className="font-bold mt-4 break-words">{selected?.name ?? deal?.productName ?? '새로운 타임딜'}</h2>{price !== undefined && <p className="text-sm line-through text-gray-400 mt-6">{formatPrice(price)}원</p>}<p className="text-3xl font-black mt-2 break-all">{formatPrice(Number(form.dealPrice) || 0)}<span className="text-sm ml-1">원</span></p><div className="border-t mt-6 pt-5 space-y-3 text-sm text-gray-600"><p>판매 수량 <strong className="float-right">{form.totalStock || 0}개</strong></p><p>1인 한도 <strong className="float-right">{form.maxPurchasePerUser || 0}개</strong></p></div><p className="text-xs text-gray-500 leading-relaxed mt-6">오픈 전까지만 수정·취소할 수 있습니다. 같은 상품의 판매 기간은 서로 겹칠 수 없습니다.</p>{error && <p role="alert" className="text-sm text-red-600 mt-5">{error}</p>}<button disabled={busy || !allowed || (user?.role === 'SELLER' && !selected)} className="w-full bg-black text-white rounded-xl py-4 mt-6 font-bold disabled:opacity-40">{busy ? '저장 중…' : id ? '변경 사항 저장' : '타임딜 등록'}</button><Link to="/seller/deals" className="block text-center mt-4 text-sm text-gray-500">돌아가기</Link></aside>
    </form>}
  </div>
}
