import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { CartItem } from '../types'

export function CartPage() {
  const [items, setItems] = useState<CartItem[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const { showToast, showConfirm } = useUi()
  const navigate = useNavigate()

  const load = async () => {
    try {
      const data = await apiFetch<CartItem[]>('/api/cart/items')
      setItems(data)
      setSelected(new Set(data.map((item) => item.id)))
      window.dispatchEvent(new CustomEvent('cart-updated', { detail: data.length }))
    } catch (error) { showToast((error as Error).message, 'danger') }
    finally { setLoading(false) }
  }

  useEffect(() => { document.title = '장바구니 | Flash Deal'; void load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const selectedItems = useMemo(() => items.filter((item) => selected.has(item.id)), [items, selected])
  const total = selectedItems.reduce((sum, item) => sum + item.price * item.quantity, 0)

  const changeQuantity = async (item: CartItem, delta: number) => {
    const quantity = Math.max(1, Math.min(item.stock, item.quantity + delta))
    if (quantity === item.quantity) return
    try {
      await apiFetch(`/api/cart/items/${item.id}`, { method: 'PATCH', body: JSON.stringify({ quantity }) })
      setItems((current) => current.map((candidate) => candidate.id === item.id ? { ...candidate, quantity } : candidate))
    } catch (error) { showToast((error as Error).message, 'danger') }
  }

  const remove = (item: CartItem) => showConfirm(`${item.productName}을(를) 장바구니에서 삭제하시겠습니까?`, async () => {
    try {
      await apiFetch(`/api/cart/items/${item.id}`, { method: 'DELETE' })
      setItems((current) => current.filter((candidate) => candidate.id !== item.id))
      setSelected((current) => { const next = new Set(current); next.delete(item.id); return next })
      window.dispatchEvent(new CustomEvent('cart-updated', { detail: items.length - 1 }))
      showToast('장바구니에서 삭제했습니다')
    } catch (error) { showToast((error as Error).message, 'danger') }
  })

  const checkout = () => {
    if (selectedItems.length === 0) { showToast('주문할 상품을 선택해주세요', 'warning'); return }
    const query = new URLSearchParams()
    selectedItems.forEach((item) => query.append('cartItemIds', String(item.id)))
    navigate(`/checkout?${query}`)
  }

  if (loading) return <Loading />

  return <div className="max-w-7xl mx-auto px-4 sm:px-8 py-16">
    <h1 className="text-5xl font-light text-gray-900 mb-12 tracking-widest uppercase">장바구니</h1>
    {items.length === 0 ? <div className="text-center py-24 border-y border-gray-200"><i className="ri-shopping-bag-line text-6xl text-gray-200" /><p className="text-gray-500 mt-5 mb-8">장바구니가 비어있습니다</p><Link to="/" className="inline-block bg-black text-white px-8 py-4 text-xs font-bold uppercase tracking-widest">쇼핑 계속하기</Link></div> :
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
        <div className="lg:col-span-2">
          <div className="flex items-center justify-between pb-4 border-b border-black"><label className="flex items-center gap-3 text-xs font-bold uppercase tracking-widest"><input type="checkbox" checked={selected.size === items.length} onChange={(e) => setSelected(e.target.checked ? new Set(items.map((item) => item.id)) : new Set())} /> 전체 선택</label><span className="text-xs text-gray-500">{items.length}개 상품</span></div>
          {items.map((item) => <article key={item.id} className="py-8 border-b border-gray-200 flex gap-4 sm:gap-7">
            <input type="checkbox" checked={selected.has(item.id)} onChange={(e) => setSelected((current) => { const next = new Set(current); e.target.checked ? next.add(item.id) : next.delete(item.id); return next })} className="mt-2" aria-label={`${item.productName} 선택`} />
            <Link to={`/products/${item.productId}`}><ProductVisual name={item.productName} className="w-24 h-32 sm:w-32 sm:h-40 flex-shrink-0" iconClassName="text-4xl" /></Link>
            <div className="flex-1 min-w-0"><div className="flex justify-between gap-4"><div><Link to={`/products/${item.productId}`} className="text-sm font-bold text-gray-900 hover:underline">{item.productName}</Link><p className="text-xs text-gray-400 mt-2">재고 {item.stock}개</p></div><button onClick={() => remove(item)} className="text-gray-400 hover:text-black h-fit" aria-label="삭제"><i className="ri-close-line text-xl" /></button></div><p className="text-base font-light mt-4">{formatPrice(item.price)}원</p><div className="flex items-end justify-between mt-5"><div className="flex border border-gray-300"><button onClick={() => void changeQuantity(item, -1)} className="w-9 h-9 hover:bg-gray-50">−</button><span className="w-10 h-9 border-x border-gray-300 flex items-center justify-center text-xs">{item.quantity}</span><button onClick={() => void changeQuantity(item, 1)} className="w-9 h-9 hover:bg-gray-50">+</button></div><p className="text-sm font-bold">{formatPrice(item.price * item.quantity)}원</p></div></div>
          </article>)}
        </div>
        <aside className="lg:col-span-1"><div className="sticky top-32 border border-gray-200 p-8"><h2 className="text-xs font-semibold uppercase tracking-widest mb-8">결제 예정 금액</h2><div className="space-y-4 pb-6 border-b border-gray-200 text-sm"><div className="flex justify-between"><span className="text-gray-500">상품금액</span><span>{formatPrice(total)}원</span></div><div className="flex justify-between"><span className="text-gray-500">배송비</span><span>무료</span></div></div><div className="flex justify-between items-end py-7"><span className="text-sm font-bold">합계</span><span className="text-2xl font-light">{formatPrice(total)}원</span></div><button onClick={checkout} className="w-full bg-black text-white py-4 text-sm font-light uppercase tracking-widest hover:bg-gray-900">선택 상품 주문하기</button></div></aside>
      </div>}
  </div>
}
