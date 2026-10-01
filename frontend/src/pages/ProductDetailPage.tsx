import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { Product } from '../types'

export function ProductDetailPage() {
  const { id } = useParams()
  const [product, setProduct] = useState<Product | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    document.title = '상품 | SpartaFashion'
    apiFetch<Product>(`/api/products/${id}`).then(setProduct).catch(() => setProduct(null)).finally(() => setLoading(false))
  }, [id])

  if (loading) return <Loading />
  if (!product) return <div className="text-center py-24"><i className="ri-error-warning-line text-5xl text-gray-300" /><p className="text-gray-500 mt-4">상품을 찾을 수 없습니다</p><Link to="/" className="inline-block mt-5 text-sm font-semibold">상품 목록으로</Link></div>

  return <div className="max-w-7xl mx-auto px-4 sm:px-6 py-12 md:py-20 animate-fade-in">
    <Link to="/" className="inline-flex items-center gap-2 text-xs font-bold text-gray-500 hover:text-black uppercase tracking-widest mb-10"><i className="ri-arrow-left-line" /> 상품 목록</Link>
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20">
      <ProductVisual name={product.name} className="aspect-square lg:aspect-[4/5] rounded-sm" iconClassName="text-8xl md:text-9xl" />
      <div className="flex flex-col justify-center">
        <p className="text-xs text-gray-400 font-bold uppercase tracking-[0.2em] mb-4">SPARTA FASHION</p>
        <h1 className="text-3xl md:text-5xl font-black text-gray-900 tracking-tight mb-5">{product.name}</h1>
        <p className="text-2xl font-light text-gray-900 mb-8">{formatPrice(product.originalPrice)}원</p>
        <p className="text-sm text-gray-600 leading-7 pb-8 border-b border-gray-200">{product.description}</p>
        <p className="mt-7 text-sm text-gray-500">구매 가능 여부는 판매 딜에서 확인해주세요.</p>
      </div>
    </div>
  </div>
}
