import { useCallback, useEffect, useState, useRef, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ProductVisual } from '../components/ProductVisual'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatDateTime, formatPrice } from '../lib/format'
import type { PageResponse, Product } from '../types'

const emptyForm = { name: '', description: '', originalPrice: '', thumbnailUrl: '' }

function ProductThumbnail({ product, className }: { product: Product; className: string }) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [product.thumbnailUrl])
  return product.thumbnailUrl && !failed
    ? <img src={product.thumbnailUrl} alt={product.name} onError={() => setFailed(true)} className={className} />
    : <ProductVisual name={product.name} className={className} />
}

export function SellerProductsPage() {
  const { user } = useAuth()
  const { showToast, showConfirm } = useUi()
  const [products, setProducts] = useState<Product[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [size, setSize] = useState(20)
  const [sort, setSort] = useState('id,desc')
  const listRequest = useRef(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const detailId = searchParams.get('productId')
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailRetry, setDetailRetry] = useState(0)
  const dialogRef = useRef<HTMLDivElement>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<number | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [detail, setDetail] = useState<Product | null>(null)
  // 기존 목록 API는 모든 판매자의 상품을 반환하므로 수정·삭제는 본인 상품에만 노출한다.
  const load = useCallback(async () => {
    const request = ++listRequest.current
    setLoading(true)
    setError('')
    try {
      const result = await apiFetch<PageResponse<Product>>(`/api/products?page=${page}&size=${size}&sort=${sort}`)
      if (request !== listRequest.current) return
      if (!result || !Array.isArray(result.content)) throw new Error('상품 목록 응답이 올바르지 않습니다.')
      if (page > 0 && page >= result.totalPages) { setPage(Math.max(0, result.totalPages - 1)); return }
      setTotalElements(result.totalElements)
      setProducts(result.content)
      setTotalPages(result.totalPages)
    } catch (e) { if (request === listRequest.current) setError((e as Error).message) }
    finally { if (request === listRequest.current) setLoading(false) }
  }, [page, size, sort])
  useEffect(() => { document.title = '상품 관리 | Flash Deal'; void load(); return () => { listRequest.current++ } }, [load])

  const edit = async (id: number) => {
    setBusy(true)
    try {
      const product = await apiFetch<Product>(`/api/products/${id}`)
      if (product.sellerId !== user?.id) throw new Error('본인 상품만 수정할 수 있습니다.')
      setEditing(id)
      setForm({ name: product.name, description: product.description ?? '', originalPrice: String(product.originalPrice), thumbnailUrl: product.thumbnailUrl ?? '' })
    } catch (e) { showToast((e as Error).message, 'danger') }
    finally { setBusy(false) }
  }
  const closeDetail = useCallback(() => {
    setSearchParams(current => { const next = new URLSearchParams(current); next.delete('productId'); return next })
  }, [setSearchParams])
  const viewDetail = (id: number) => {
    setSearchParams(current => { const next = new URLSearchParams(current); next.set('productId', String(id)); return next })
  }
  useEffect(() => {
    setDetail(null); setDetailError('')
    if (!detailId) { setDetailLoading(false); return }
    if (!/^\d+$/.test(detailId) || !Number.isSafeInteger(Number(detailId)) || Number(detailId) <= 0) {
      setDetailError('올바른 상품 번호가 아닙니다.'); setDetailLoading(false); return
    }
    const controller = new AbortController()
    setDetailLoading(true)
    apiFetch<Product>(`/api/products/${detailId}`, { signal: controller.signal })
      .then(product => { if (!controller.signal.aborted) setDetail(product) })
      .catch((e: Error) => { if (!controller.signal.aborted) setDetailError(e.message) })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false) })
    return () => controller.abort()
  }, [detailId, detailRetry])
  useEffect(() => {
    if (!detailId) return
    const previousFocus = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.querySelector<HTMLButtonElement>('button')?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDetail()
      if (event.key !== 'Tab') return
      const controls = dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled)')
      if (!controls?.length) return
      const first = controls[0], last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); document.body.style.overflow = previousOverflow; previousFocus?.focus() }
  }, [detailId, closeDetail])
  const save = async (event: FormEvent) => {
    event.preventDefault()
    const price = Number(form.originalPrice)
    if (!form.name.trim() || !Number.isFinite(price) || price <= 0) { showToast('상품명과 0보다 큰 가격을 입력하세요.', 'warning'); return }
    setBusy(true)
    try {
      const saved = await apiFetch<Product>(editing === null ? '/api/products' : `/api/products/${editing}`, {
        method: editing === null ? 'POST' : 'PATCH',
        body: JSON.stringify({ ...form, name: form.name.trim(), originalPrice: price })
      })
      showToast(editing === null ? '상품을 등록했습니다.' : '상품을 수정했습니다.')
      setEditing(null)
      setForm(emptyForm)
      if (detailId === String(saved.id)) setDetail(saved)
      if (editing === null && page !== 0) setPage(0)
      else await load()
    } catch (e) { showToast((e as Error).message, 'danger') }
    finally { setBusy(false) }
  }
  const remove = (product: Product) => showConfirm(`${product.name} 상품을 삭제할까요?`, () => {
    void (async () => {
      setBusy(true)
      try {
        await apiFetch<void>(`/api/products/${product.id}`, { method: 'DELETE' })
        if (detailId === String(product.id)) closeDetail()
        if (editing === product.id) { setEditing(null); setForm(emptyForm) }
        showToast('상품을 삭제했습니다.')
        await load()
      } catch (e) { showToast((e as Error).message, 'danger') }
      finally { setBusy(false) }
    })()
  })

  return <div className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
    <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
      <div><p className="text-xs font-bold text-gray-500 mb-2">SELLER CENTER</p><h1 className="text-3xl font-black">판매자 상품 관리</h1><p className="text-gray-500 mt-3">등록된 상품을 조회하고 본인 상품을 등록·수정·삭제하세요.</p></div>
      <button type="button" disabled={loading || busy} onClick={() => void load()} className="inline-flex items-center gap-2 border rounded-lg px-4 py-3 text-sm disabled:opacity-50"><i className="ri-refresh-line" aria-hidden="true" />목록 새로고침</button>
    </div>
    <form onSubmit={save} className="bg-gray-50 border rounded-xl p-6 mb-10 space-y-4">
      <h2 className="text-lg font-bold">{editing === null ? '새 상품 등록' : `상품 #${editing} 수정`}</h2>
      <fieldset disabled={busy} className="grid sm:grid-cols-2 gap-4 disabled:opacity-60">
        <label className="text-sm font-medium">상품명<input required maxLength={100} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="block border rounded p-3 w-full mt-2" /></label>
        <label className="text-sm font-medium">정가 (원)<input required type="number" min="0.01" step="0.01" value={form.originalPrice} onChange={e => setForm({ ...form, originalPrice: e.target.value })} className="block border rounded p-3 w-full mt-2" /></label>
        <label className="text-sm font-medium sm:col-span-2">설명<textarea maxLength={2000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} className="block border rounded p-3 w-full mt-2" /></label>
        <label className="text-sm font-medium sm:col-span-2">썸네일 URL<input type="url" maxLength={500} value={form.thumbnailUrl} onChange={e => setForm({ ...form, thumbnailUrl: e.target.value })} className="block border rounded p-3 w-full mt-2" /></label>
        <div className="flex gap-3"><button className="bg-black text-white rounded px-5 py-3" type="submit">{busy ? '처리 중…' : editing === null ? '등록' : '수정 저장'}</button>{editing !== null && <button type="button" onClick={() => { setEditing(null); setForm(emptyForm) }} className="border rounded px-5 py-3">취소</button>}</div>
      </fieldset>
    </form>
    <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
      <div><h2 className="text-xl font-bold">상품 목록</h2><p className="text-sm text-gray-500 mt-1">전체 활성 상품 {totalElements}개 · 본인 상품만 수정·삭제할 수 있습니다.</p></div>
      <div className="flex flex-wrap gap-3">
        <label className="text-sm">정렬 <select aria-label="상품 정렬" disabled={loading || busy} value={sort} onChange={e => { setSort(e.target.value); setPage(0) }} className="border rounded p-2 ml-2"><option value="id,desc">최신 등록순</option><option value="id,asc">오래된 등록순</option><option value="originalPrice,asc">낮은 가격순</option><option value="originalPrice,desc">높은 가격순</option><option value="name,asc">상품명순</option></select></label>
        <label className="text-sm">페이지당 <select aria-label="페이지당 상품 수" disabled={loading || busy} value={size} onChange={e => { setSize(Number(e.target.value)); setPage(0) }} className="border rounded p-2 ml-2"><option value={20}>20개</option><option value={50}>50개</option></select></label>
      </div>
    </div>
    {error && <div role="alert" className="text-red-600 mb-6">{error} <button onClick={() => void load()} className="underline">다시 시도</button></div>}
    {loading ? <p role="status">상품을 불러오는 중…</p> : !error && <>
      {products.length === 0 && <p className="text-gray-500 py-10 text-center">등록된 상품이 없습니다.</p>}
      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">{products.map(product => <article key={product.id} className="border rounded-xl p-5">
        <button type="button" disabled={busy} onClick={() => viewDetail(product.id)} aria-label={`${product.name} 상세 보기`} className="block w-full mb-4"><ProductThumbnail product={product} className="w-full h-40 object-cover rounded" /></button>
        <h3 className="font-bold"><button type="button" disabled={busy} onClick={() => viewDetail(product.id)} className="text-left hover:underline">{product.name}</button></h3><p className="mt-2">{formatPrice(product.originalPrice)}원</p><p className="text-sm text-gray-500 mt-2 whitespace-pre-wrap">{product.description}</p>
        <p className="text-xs text-gray-500 mt-3">상품 #{product.id} · 판매자 #{product.sellerId} {product.sellerId === user?.id ? '(내 상품)' : ''}</p><p className="text-xs text-gray-500 mt-1">{product.status === 'ACTIVE' ? '활성' : '비활성'} · 등록 {formatDateTime(product.createdAt)}</p>
        <div className="flex flex-wrap gap-3 mt-4"><button disabled={busy} onClick={() => void viewDetail(product.id)} className="border rounded px-4 py-2 disabled:opacity-50">상세 조회</button>
        {product.sellerId === user?.id && <><Link to={`/seller/deals/new?productId=${product.id}`} className="bg-black text-white rounded px-4 py-2">딜 등록</Link><button disabled={busy} onClick={() => void edit(product.id)} className="border rounded px-4 py-2 disabled:opacity-50">수정</button><button disabled={busy} onClick={() => remove(product)} className="text-red-600 border rounded px-4 py-2 disabled:opacity-50">삭제</button></>}</div>
      </article>)}</div>
      <div className="flex justify-center items-center gap-4 mt-8"><button disabled={page === 0 || busy || loading} onClick={() => setPage(page - 1)} className="disabled:opacity-30">이전</button><span>{totalPages === 0 ? 0 : page + 1} / {totalPages}</span><button disabled={page + 1 >= totalPages || busy || loading} onClick={() => setPage(page + 1)} className="disabled:opacity-30">다음</button></div>
    </>}
    {detailId && <div className="fixed inset-0 z-[1000] flex items-center justify-center px-4" role="dialog" aria-modal="true" aria-labelledby="product-detail-title">
      <button type="button" className="absolute inset-0 bg-black/50" aria-label="상품 상세 닫기" onClick={closeDetail} />
      <div ref={dialogRef} className="relative bg-white rounded-2xl p-6 w-full max-w-lg max-h-[85vh] overflow-auto">
        <div className="flex items-start justify-between gap-4"><h2 id="product-detail-title" className="text-xl font-bold">상품 상세</h2><button type="button" onClick={closeDetail} className="border rounded px-3 py-1" aria-label="닫기">닫기</button></div>
        {detailLoading && <p role="status" className="py-10 text-center">상품 상세를 불러오는 중…</p>}
        {detailError && <div role="alert" className="py-8 text-red-600"><p>{detailError}</p><button type="button" onClick={() => setDetailRetry(detailRetry + 1)} className="underline mt-4">다시 시도</button></div>}
        {!detailLoading && !detailError && detail && <>
          <ProductThumbnail product={detail} className="w-full h-56 object-cover rounded-lg mt-5" />
          <h3 className="text-xl font-bold mt-5">{detail.name}</h3>
          <p className="text-2xl font-bold mt-3">{formatPrice(detail.originalPrice)}원</p><p className="text-sm text-gray-600 whitespace-pre-wrap mt-4">{detail.description || '등록된 설명이 없습니다.'}</p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-3 mt-6 text-sm"><dt>상품 번호</dt><dd>{detail.id}</dd><dt>판매자 번호</dt><dd>{detail.sellerId}{detail.sellerId === user?.id ? ' (내 상품)' : ''}</dd><dt>상태</dt><dd>{detail.status === 'ACTIVE' ? '활성 (ACTIVE)' : '비활성 (INACTIVE)'}</dd><dt>등록 일시</dt><dd>{formatDateTime(detail.createdAt)}</dd><dt>썸네일 URL</dt><dd className="break-all">{detail.thumbnailUrl || '등록되지 않음'}</dd></dl>
          <div className="flex flex-wrap gap-3 mt-6"><button type="button" disabled={busy} onClick={() => setDetailRetry(detailRetry + 1)} className="border rounded px-4 py-3 disabled:opacity-50">상세 새로고침</button>
            {detail.sellerId === user?.id && <><button type="button" disabled={busy} onClick={() => { const id = detail.id; closeDetail(); void edit(id) }} className="bg-black text-white rounded px-5 py-3 disabled:opacity-50">이 상품 수정</button><button type="button" disabled={busy} onClick={() => { closeDetail(); remove(detail) }} className="text-red-600 border rounded px-4 py-3 disabled:opacity-50">삭제</button></>}
          </div>
        </>}
      </div>
    </div>}
  </div>
}
