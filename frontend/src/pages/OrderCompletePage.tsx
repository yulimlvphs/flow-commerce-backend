import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { apiFetch } from '../lib/api'
import { formatDate, formatPrice } from '../lib/format'
import type { Order } from '../types'

export function OrderCompletePage() {
  const [params] = useSearchParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const orderId = params.get('orderId')
  useEffect(() => {
    document.title = '주문 완료 | SpartaFashion'
    if (!orderId) { setError('주문 정보가 없습니다'); setLoading(false); return }
    apiFetch<Order>(`/api/orders/${orderId}`).then(setOrder).catch(() => setError('주문 정보를 불러올 수 없습니다')).finally(() => setLoading(false))
  }, [orderId])
  if (loading) return <Loading />
  if (error || !order) return <p className="text-center py-24 text-gray-500 text-sm">{error}</p>
  return <div className="max-w-2xl mx-auto px-4 sm:px-6 py-20 text-center">
    <div className="animate-bounce-in mb-12"><div className="w-28 h-28 bg-black rounded-full flex items-center justify-center mx-auto border-4 border-gray-200"><i className="ri-check-line text-6xl text-white font-light" /></div></div>
    <h1 className="text-5xl font-light text-gray-900 mb-4 animate-fade-in tracking-widest uppercase">주문 완료</h1><p className="text-sm text-gray-600 mb-12 animate-fade-in uppercase tracking-widest font-medium">구매해 주셔서 감사합니다</p>
    <div className="bg-white border border-gray-200 p-10 mb-12 animate-scale-in space-y-6 text-left"><div className="flex justify-between pb-6 border-b border-gray-200"><span className="text-sm text-gray-600">주문번호</span><span className="font-mono font-medium text-sm">{order.orderId}</span></div><div className="flex justify-between pb-6 border-b border-gray-200"><span className="text-sm text-gray-600">주문일시</span><span className="text-sm">{formatDate(order.createdAt)}</span></div><div className="flex justify-between pb-6 border-b border-gray-200"><span className="text-sm text-gray-600">주문상품</span><span className="text-sm">{order.orderName}</span></div><div className="flex justify-between"><span className="text-sm font-medium uppercase tracking-wider">총 결제금액</span><span className="text-lg font-light">{formatPrice(order.totalPrice)}원</span></div></div>
    <div className="border border-gray-200 p-8 mb-12 animate-fade-in text-left"><h2 className="text-xs font-semibold mb-8 uppercase tracking-widest">다음 안내</h2><ul className="space-y-6">{['주문 확인 이메일이 발송되었습니다', '주문하신 상품은 1~2 영업일 내 정성껏 포장하여 발송됩니다', '주문내역에서 언제든지 배송 현황을 확인하실 수 있습니다'].map((text, index) => <li key={text} className="flex items-start gap-4"><span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-black text-white text-sm font-light flex-shrink-0">{index + 1}</span><span className="text-sm text-gray-600 pt-0.5">{text}</span></li>)}</ul></div>
    <div className="flex gap-4 animate-fade-in"><Link to="/orders" className="flex-1 bg-black hover:bg-gray-900 text-white font-light py-4 text-sm uppercase tracking-widest">주문내역 보기</Link><Link to="/" className="flex-1 border border-gray-300 text-gray-900 hover:bg-gray-50 font-light py-4 text-sm uppercase tracking-widest">쇼핑 계속하기</Link></div>
  </div>
}
