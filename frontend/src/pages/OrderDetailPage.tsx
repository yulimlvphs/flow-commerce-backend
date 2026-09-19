import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatDateTime, formatPrice } from '../lib/format'
import type { Order } from '../types'

const statusInfo = {
  PENDING_PAYMENT: { label: '결제 대기', color: 'text-amber-600 bg-amber-50' },
  CONFIRMED: { label: '결제 완료', color: 'text-emerald-600 bg-emerald-50' },
  CANCELLED: { label: '주문 취소', color: 'text-gray-500 bg-gray-100' }
}

export function OrderDetailPage() {
  const { orderId } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const { showToast, showConfirm } = useUi()

  const load = async () => {
    try { setOrder(await apiFetch<Order>(`/api/orders/${orderId}`)); setNotFound(false) }
    catch { setNotFound(true) }
    finally { setLoading(false) }
  }
  useEffect(() => { document.title = '주문 상세 | SpartaFashion'; void load() }, [orderId]) // eslint-disable-line react-hooks/exhaustive-deps

  const cancel = () => order && showConfirm('이 주문을 취소하시겠습니까? 결제 금액이 환불됩니다.', async () => {
    try { await apiFetch(`/api/payments/${order.paymentId}/cancel`, { method: 'POST', body: JSON.stringify({ reason: '사용자 요청 취소' }) }); showToast('주문이 취소되었습니다'); await load() }
    catch (error) { showToast(`취소 실패: ${(error as Error).message}`, 'danger') }
  })

  if (loading) return <Loading />
  if (notFound || !order) return <div className="text-center py-24"><i className="ri-error-warning-line text-5xl text-gray-300" /><p className="text-gray-500 mt-4 mb-5">주문을 찾을 수 없습니다</p><Link to="/orders" className="text-sm font-semibold"><i className="ri-arrow-left-line" /> 주문내역으로 돌아가기</Link></div>
  const info = statusInfo[order.status]

  return <div className="max-w-5xl mx-auto px-4 sm:px-6 py-16 animate-fade-in">
    <div className="flex items-start justify-between mb-12"><div><Link to="/orders" className="text-xs text-gray-500 hover:text-black uppercase tracking-widest"><i className="ri-arrow-left-line" /> 주문내역</Link><h1 className="text-4xl md:text-5xl font-light tracking-widest uppercase mt-6">주문 상세</h1></div><span className={`text-xs font-bold px-4 py-2 rounded-full ${info.color}`}>{info.label}</span></div>
    <div className="grid grid-cols-1 md:grid-cols-3 gap-px bg-gray-200 border border-gray-200 mb-12"><div className="bg-white p-7"><p className="text-[10px] text-gray-500 font-black uppercase tracking-widest mb-2">주문번호</p><p className="font-mono text-sm">#{order.orderId}</p></div><div className="bg-white p-7"><p className="text-[10px] text-gray-500 font-black uppercase tracking-widest mb-2">주문일시</p><p className="text-sm">{formatDateTime(order.createdAt)}</p></div><div className="bg-white p-7"><p className="text-[10px] text-gray-500 font-black uppercase tracking-widest mb-2">결제 금액</p><p className="text-sm font-bold">{formatPrice(order.totalPrice)}원</p></div></div>
    <section className="mb-12"><h2 className="text-xs font-bold uppercase tracking-widest mb-5">주문 상품</h2><div className="border-t border-black">{order.orderItems.map((item, index) => <div key={`${item.productName}-${index}`} className="flex justify-between items-center py-6 border-b border-gray-200"><div><p className="text-sm font-semibold">{item.productName}</p><p className="text-xs text-gray-500 mt-2">수량 {item.quantity}개 · 개당 {formatPrice(item.orderPrice)}원</p></div><p className="text-sm font-bold">{formatPrice(item.orderPrice * item.quantity)}원</p></div>)}</div><div className="flex justify-between items-center py-7"><span className="text-sm font-bold uppercase tracking-widest">총 결제금액</span><span className="text-2xl font-light">{formatPrice(order.totalPrice)}원</span></div></section>
    <div className="flex flex-col sm:flex-row justify-end gap-3"><Link to="/orders" className="px-8 py-4 border-2 border-black text-black text-sm font-black text-center uppercase tracking-wide rounded-lg hover:bg-black hover:text-white transition-all">주문내역으로 돌아가기</Link>{order.status === 'PENDING_PAYMENT' && <span className="px-8 py-4 border-2 border-gray-200 text-gray-400 text-sm font-black text-center uppercase tracking-wide rounded-lg">결제 미완료 주문</span>}{order.status === 'CONFIRMED' && <button onClick={cancel} className="px-8 py-4 bg-red-600 text-white text-sm font-black uppercase tracking-wide rounded-lg hover:bg-red-700">주문 취소</button>}</div>
  </div>
}
