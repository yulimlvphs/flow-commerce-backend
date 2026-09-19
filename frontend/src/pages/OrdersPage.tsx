import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatDate, formatPrice } from '../lib/format'
import type { Order } from '../types'

const statusInfo = {
  PENDING_PAYMENT: { label: '결제 대기', color: 'text-amber-600 bg-amber-50' },
  CONFIRMED: { label: '결제 완료', color: 'text-emerald-600 bg-emerald-50' },
  CANCELLED: { label: '주문 취소', color: 'text-gray-500 bg-gray-100' }
}

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const { showToast, showConfirm } = useUi()

  const load = async () => {
    try { setOrders(await apiFetch<Order[]>('/api/orders')) }
    catch (error) { showToast((error as Error).message, 'danger') }
    finally { setLoading(false) }
  }
  useEffect(() => { document.title = '주문내역 | SpartaFashion'; void load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const cancel = (order: Order) => showConfirm('이 주문을 취소하시겠습니까? 결제 금액이 환불됩니다.', async () => {
    try { await apiFetch(`/api/payments/${order.paymentId}/cancel`, { method: 'POST', body: JSON.stringify({ reason: '사용자 요청 취소' }) }); showToast('주문이 취소되었습니다'); await load() }
    catch (error) { showToast(`취소 실패: ${(error as Error).message}`, 'danger') }
  })

  if (loading) return <Loading />
  return <div className="max-w-5xl mx-auto px-4 sm:px-6 py-16">
    <h1 className="text-5xl font-light text-gray-900 mb-12 tracking-widest uppercase">주문내역</h1>
    {orders.length === 0 ? <div className="text-center py-24 border-y border-gray-200"><i className="ri-file-list-3-line text-6xl text-gray-200" /><p className="text-gray-500 mt-5 mb-8">주문내역이 없습니다</p><Link to="/" className="inline-block bg-black text-white px-8 py-4 text-xs font-bold uppercase tracking-widest">쇼핑하러 가기</Link></div> :
      <div className="border-t border-black">
        {orders.map((order) => {
          const info = statusInfo[order.status]
          const open = expanded.has(order.orderId)
          return <article key={order.orderId} className="border-b border-gray-200">
            <button onClick={() => setExpanded((current) => { const next = new Set(current); open ? next.delete(order.orderId) : next.add(order.orderId); return next })} className="w-full px-4 sm:px-8 py-6 bg-white flex items-center justify-between text-left hover:bg-gray-50 transition-colors">
              <div><p className="text-xs text-gray-400 mb-2">{formatDate(order.createdAt)}</p><div className="flex items-center gap-3"><Link onClick={(e) => e.stopPropagation()} to={`/orders/${order.orderId}`} className="text-sm font-mono font-medium hover:underline">#{order.orderId}</Link><span className={`text-[10px] font-bold px-2.5 py-1 rounded-full ${info.color}`}>{info.label}</span></div><p className="text-sm font-semibold mt-3">{order.orderName}</p></div>
              <div className="flex items-center gap-6"><span className="text-base font-light">{formatPrice(order.totalPrice)}원</span><i className={`${open ? 'ri-arrow-up-s-line' : 'ri-arrow-down-s-line'} text-xl text-gray-400`} /></div>
            </button>
            {open && <div className="bg-gray-50 px-4 sm:px-8 py-6 animate-fade-in"><div className="space-y-3">{order.orderItems.map((item, index) => <div key={`${item.productName}-${index}`} className="flex justify-between text-sm"><span className="text-gray-600">{item.productName} × {item.quantity}</span><span>{formatPrice(item.orderPrice * item.quantity)}원</span></div>)}</div><div className="mt-6 pt-5 border-t border-gray-200 flex justify-end items-center gap-5"><Link to={`/orders/${order.orderId}`} className="text-xs font-bold uppercase tracking-wider hover:underline">상세보기</Link>{order.status === 'PENDING_PAYMENT' && <span className="text-xs text-gray-400">결제 미완료 주문</span>}{order.status === 'CONFIRMED' && <button onClick={() => cancel(order)} className="text-xs font-bold text-red-600 uppercase tracking-wider">주문 취소</button>}</div></div>}
          </article>
        })}
      </div>}
  </div>
}
