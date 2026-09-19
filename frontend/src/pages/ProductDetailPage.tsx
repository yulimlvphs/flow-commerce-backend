import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { Product } from '../types'

export function ProductDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { isLoggedIn } = useAuth()
  const { showToast } = useUi()
  const [product, setProduct] = useState<Product | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    document.title = '상품 | SpartaFashion'
    apiFetch<Product>(`/api/products/${id}`).then(setProduct).catch(() => setProduct(null)).finally(() => setLoading(false))
  }, [id])

  if (loading) return <Loading />
  if (!product) return <div className="text-center py-24"><i className="ri-error-warning-line text-5xl text-gray-300" /><p className="text-gray-500 mt-4">상품을 찾을 수 없습니다</p><Link to="/" className="inline-block mt-5 text-sm font-semibold">상품 목록으로</Link></div>

  const addToCart = async () => {
    if (!isLoggedIn) { showToast('로그인이 필요합니다', 'warning'); navigate('/login'); return }
    setAdding(true)
    try {
      await apiFetch('/api/cart/items', { method: 'POST', body: JSON.stringify({ productId: product.id, quantity }) })
      showToast('장바구니에 담았습니다')
      window.dispatchEvent(new CustomEvent('cart-updated'))
    } catch (error) { showToast(`장바구니 추가 실패: ${(error as Error).message}`, 'danger') }
    finally { setAdding(false) }
  }

  return <div className="max-w-7xl mx-auto px-4 sm:px-6 py-12 md:py-20 animate-fade-in">
    <Link to="/" className="inline-flex items-center gap-2 text-xs font-bold text-gray-500 hover:text-black uppercase tracking-widest mb-10"><i className="ri-arrow-left-line" /> 상품 목록</Link>
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20">
      <ProductVisual name={product.name} className="aspect-square lg:aspect-[4/5] rounded-sm" iconClassName="text-8xl md:text-9xl" />
      <div className="flex flex-col justify-center">
        <p className="text-xs text-gray-400 font-bold uppercase tracking-[0.2em] mb-4">SPARTA FASHION</p>
        <h1 className="text-3xl md:text-5xl font-black text-gray-900 tracking-tight mb-5">{product.name}</h1>
        <p className="text-2xl font-light text-gray-900 mb-8">{formatPrice(product.price)}원</p>
        <p className="text-sm text-gray-600 leading-7 pb-8 border-b border-gray-200">{product.description}</p>
        <div className="py-7 border-b border-gray-200">
          {product.stock > 5 ? <span className="text-xs font-black text-emerald-700"><span className="inline-block w-2 h-2 bg-emerald-500 rounded-full mr-2" />재고 {product.stock}개</span> : product.stock > 0 ? <span className="text-xs font-black text-amber-700">남은 재고 {product.stock}개</span> : <span className="text-xs font-black text-gray-500">품절</span>}
        </div>
        {product.stock > 0 && <div className="mt-8">
          <p className="text-xs font-bold uppercase tracking-widest mb-3">수량</p>
          <div className="flex border border-gray-300 w-fit mb-6"><button onClick={() => setQuantity((q) => Math.max(1, q - 1))} className="w-11 h-11 text-lg hover:bg-gray-50">−</button><span className="w-14 h-11 flex items-center justify-center border-x border-gray-300 text-sm">{quantity}</span><button onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))} className="w-11 h-11 text-lg hover:bg-gray-50">+</button></div>
          <button disabled={adding} onClick={() => void addToCart()} className="w-full bg-black text-white py-4 text-sm font-bold uppercase tracking-widest hover:bg-gray-900 disabled:opacity-50 flex justify-center">{adding ? <span className="spinner" /> : '장바구니 담기'}</button>
        </div>}
      </div>
    </div>
  </div>
}
