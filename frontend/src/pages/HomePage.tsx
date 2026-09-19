import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ProductVisual } from '../components/ProductVisual'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { Product } from '../types'

function ProductSkeleton() {
  return <div className="animate-fade-in"><div className="skeleton skeleton-img aspect-[3/4] rounded-lg mb-3" /><div className="space-y-2"><div className="skeleton skeleton-text w-2/3 h-3" /><div className="skeleton skeleton-text-lg w-full h-4" /><div className="skeleton skeleton-text w-1/2 h-3" /></div></div>
}

export function HomePage() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const { isLoggedIn } = useAuth()
  const { showToast } = useUi()
  const navigate = useNavigate()

  useEffect(() => {
    document.title = '홈 | SpartaFashion'
    apiFetch<Product[]>('/api/products').then(setProducts).catch((error: Error) => showToast(`상품을 불러오지 못했습니다: ${error.message}`, 'danger')).finally(() => setLoading(false))
  }, [showToast])

  const addToCart = async (productId: number) => {
    if (!isLoggedIn) {
      showToast('로그인이 필요합니다', 'warning')
      navigate('/login')
      return
    }
    try {
      await apiFetch('/api/cart/items', { method: 'POST', body: JSON.stringify({ productId, quantity: 1 }) })
      showToast('장바구니에 담았습니다')
      window.dispatchEvent(new CustomEvent('cart-updated'))
    } catch (error) {
      showToast(`장바구니 추가 실패: ${(error as Error).message}`, 'danger')
    }
  }

  return <>
    <section className="relative overflow-hidden bg-black text-white hero-gradient-animate hero-noise">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-14 md:py-20 relative z-10">
        <div className="flex items-center justify-between animate-fade-in">
          <div><h1 className="text-4xl sm:text-5xl md:text-6xl font-black leading-[0.95] tracking-tighter">나만의 스타일</h1><p className="text-gray-400 text-sm md:text-base leading-relaxed max-w-md mt-4">트렌디한 스트릿 스타일부터 클래식까지. 당신만의 패션 감각을 표현하세요.</p></div>
          <a href="#products" className="hidden sm:inline-flex items-center gap-3 px-8 py-4 bg-white text-black font-black text-sm uppercase tracking-wide hover:bg-gray-100 transition-all flex-shrink-0">둘러보기 <i className="ri-arrow-right-line" /></a>
        </div>
      </div>
    </section>

    <section id="products" className="max-w-7xl mx-auto px-4 sm:px-6 py-20">
      <div className="flex items-end justify-between mb-12"><div><h2 className="text-3xl md:text-4xl font-black text-gray-900 uppercase tracking-tight">신상품</h2><p className="text-xs text-gray-500 mt-3 font-semibold"><span className="font-black text-gray-900">{products.length}</span> 개 상품</p></div></div>
      {loading && <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">{Array.from({ length: 4 }).map((_, i) => <ProductSkeleton key={i} />)}</div>}
      {!loading && products.length === 0 && <div className="text-center py-20 animate-fade-in"><i className="ri-inbox-2-line text-5xl text-gray-300 mb-4 inline-block" /><p className="text-gray-500 font-medium text-lg">상품이 없습니다</p><p className="text-gray-400 text-sm mt-1">곧 새로운 상품이 입고됩니다</p></div>}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 md:gap-6">
        {products.map((product, index) => (
          <article key={product.id} className="product-card rounded-2xl overflow-hidden animate-fade-in" style={{ animationDelay: `${index * 0.05}s` }}>
            <Link to={`/products/${product.id}`} className="block">
              <div className="relative aspect-[3/4] overflow-hidden">
                <ProductVisual name={product.name} className="card-img w-full h-full" iconClassName="text-6xl" />
                <div className="card-overlay absolute inset-0 bg-gradient-to-t from-black/60 via-black/0 to-transparent flex flex-col justify-end p-5">
                  {product.stock > 0 && <button onClick={(event) => { event.preventDefault(); void addToCart(product.id) }} className="w-full py-3 bg-white text-black text-xs font-black uppercase tracking-wider rounded-full hover:bg-gray-100 transition-colors">담기</button>}
                </div>
                {index < 3 && <span className="absolute top-3 left-3 text-[10px] font-black text-white bg-black/80 backdrop-blur-sm px-3 py-1.5 rounded-full">신상</span>}
                {product.stock <= 5 && product.stock > 0 && <span className="absolute top-3 right-3 text-[10px] font-black text-white bg-red-500/90 backdrop-blur-sm px-3 py-1.5 rounded-full">품절임박</span>}
                {product.stock === 0 && <div className="absolute inset-0 bg-white/70 backdrop-blur-[2px] flex items-center justify-center"><span className="text-sm font-black text-gray-600 bg-white px-5 py-2 rounded-full shadow-sm">품절</span></div>}
              </div>
              <div className="card-info p-4 pb-5"><p className="text-[11px] text-gray-400 font-bold uppercase tracking-wider mb-1">SPARTA FASHION</p><h3 className="text-sm font-bold text-gray-900 line-clamp-1">{product.name}</h3><p className="text-base font-black text-gray-900 mt-2">{formatPrice(product.price)}원</p></div>
            </Link>
          </article>
        ))}
      </div>
    </section>
  </>
}
