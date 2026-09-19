import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { ProductVisual } from '../components/ProductVisual'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import { formatPrice } from '../lib/format'
import type { CheckoutData, OrderCheckoutResponse, PortOneConfig, PortOnePaymentResponse } from '../types'

declare global {
  interface Window {
    PortOne?: { requestPayment: (request: Record<string, unknown>) => Promise<PortOnePaymentResponse> }
  }
}

export function CheckoutPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { member } = useAuth()
  const { showToast } = useUi()
  const [checkout, setCheckout] = useState<CheckoutData | null>(null)
  const [config, setConfig] = useState<PortOneConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [buttonLabel, setButtonLabel] = useState('결제하기')
  const cartItemIds = params.getAll('cartItemIds').map(Number).filter(Number.isFinite)

  useEffect(() => {
    document.title = '주문서 | SpartaFashion'
    if (!document.querySelector('script[data-portone-sdk]')) {
      const script = document.createElement('script')
      script.src = 'https://cdn.portone.io/v2/browser-sdk.js'
      script.async = true
      script.dataset.portoneSdk = 'true'
      document.head.appendChild(script)
    }
    const query = cartItemIds.length ? `?${cartItemIds.map((id) => `cartItemIds=${id}`).join('&')}` : ''
    Promise.allSettled([apiFetch<PortOneConfig>('/api/config/portone'), apiFetch<CheckoutData>(`/api/orders/checkout${query}`)])
      .then(([configResult, checkoutResult]) => {
        if (checkoutResult.status === 'rejected') throw checkoutResult.reason
        setCheckout(checkoutResult.value)
        if (configResult.status === 'fulfilled') setConfig(configResult.value)
      })
      .catch(() => { showToast('주문서를 불러올 수 없습니다. 주문내역으로 이동합니다', 'warning'); window.setTimeout(() => navigate('/orders'), 1500) })
      .finally(() => setLoading(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const startPayment = async () => {
    if (!checkout) return
    if (!config || !window.PortOne) { showToast('결제 설정이 아직 준비되지 않았습니다', 'warning'); return }
    setPaying(true); setButtonLabel('처리 중...')
    try {
      const orderBody = cartItemIds.length ? { cartItemIds } : {}
      const order = await apiFetch<OrderCheckoutResponse>('/api/orders/checkout', { method: 'POST', body: JSON.stringify(orderBody) })
      setButtonLabel('결제창 여는 중...')
      const customer: Record<string, string> = {}
      if (member?.email) customer.email = member.email
      const response = await window.PortOne.requestPayment({ storeId: config.storeId, channelKey: config.channelKey, paymentId: order.portonePaymentId, orderName: order.orderName, totalAmount: order.totalPrice, currency: 'KRW', payMethod: 'CARD', customer })
      if (response.code === undefined) {
        setButtonLabel('결제 확인 중...')
        await apiFetch('/api/payments/confirm', { method: 'POST', body: JSON.stringify({ orderId: order.orderId, portonePaymentId: response.paymentId }) })
        window.dispatchEvent(new CustomEvent('cart-updated'))
        navigate(`/orders/complete?orderId=${order.orderId}`)
      } else {
        showToast('결제가 취소되었습니다. 주문내역에서 확인해주세요', 'warning')
        window.setTimeout(() => navigate('/orders'), 1500)
      }
    } catch (error) { showToast(`결제 오류: ${(error as Error).message}`, 'danger'); setPaying(false); setButtonLabel('결제하기') }
  }

  if (loading || !checkout) return <Loading />
  return <div className="max-w-7xl mx-auto px-4 sm:px-8 py-16 animate-fade-in">
    <h1 className="text-5xl font-light text-gray-900 mb-12 tracking-widest uppercase">주문서</h1>
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
      <div className="lg:col-span-2">
        <section className="mb-16"><h2 className="text-xs font-semibold text-gray-900 mb-8 uppercase tracking-widest">주문 상품</h2><div className="border-b border-gray-200 pb-8">{checkout.items.map((item) => <div key={item.productId} className="py-6 flex gap-6 border-b border-gray-200 last:border-0"><ProductVisual name={item.productName} className="w-24 h-32 rounded-sm flex-shrink-0 border border-gray-200" iconClassName="text-2xl" /><div className="flex-1 flex flex-col justify-between"><div><p className="text-sm font-medium text-gray-900 tracking-tight">{item.productName}</p><p className="text-xs text-gray-500 mt-2">수량: {item.quantity}</p></div><p className="text-base font-light text-gray-900">{formatPrice(item.subtotal)}원</p></div></div>)}</div></section>
        <section className="mb-16"><h2 className="text-xs font-semibold text-gray-900 mb-8 uppercase tracking-widest">배송 정보</h2><div className="border border-gray-200 p-6"><p className="text-sm text-gray-600 mb-2">일반 배송</p><p className="text-xs text-gray-500 font-medium">예상 배송일: 영업일 기준 5~7일</p><p className="text-xs text-gray-500 font-medium mt-1">전 주문 무료배송</p></div></section>
        <section><h2 className="text-xs font-semibold text-gray-900 mb-8 uppercase tracking-widest">결제 수단</h2><div className="border border-gray-200 p-6 flex items-center gap-4"><i className="ri-bank-card-line text-2xl text-gray-400" /><div><p className="text-sm font-medium text-gray-900">신용카드 / 체크카드</p><p className="text-xs text-gray-500 mt-1">PortOne 안전결제</p></div></div></section>
      </div>
      <aside><div className="sticky top-32 bg-white border border-gray-200 p-8"><h2 className="text-xs font-semibold text-gray-900 mb-8 uppercase tracking-widest">결제 금액</h2><div className="space-y-4 mb-8 pb-8 border-b border-gray-200 text-sm"><div className="flex justify-between"><span className="text-gray-600">상품금액</span><span>{formatPrice(checkout.totalPrice)}원</span></div><div className="flex justify-between"><span className="text-gray-600">배송비</span><span>무료</span></div><div className="flex justify-between"><span className="text-gray-600">부가세</span><span>포함</span></div></div><div className="flex justify-between mb-8"><span className="text-sm font-medium uppercase tracking-wide">합계</span><span className="text-xl font-light">{formatPrice(checkout.totalPrice)}원</span></div><button disabled={paying} onClick={() => void startPayment()} className="w-full bg-black hover:bg-gray-900 text-white font-light py-4 text-sm mb-4 uppercase tracking-widest disabled:opacity-60">{buttonLabel}</button><Link to="/cart" className="block text-center text-xs text-gray-500 hover:text-gray-900 py-3 uppercase tracking-wider font-medium">장바구니로 돌아가기</Link><div className="text-center mt-8 pt-6 border-t border-gray-200"><p className="text-xs text-gray-500"><i className="ri-shield-check-line text-sm mr-2" />안전결제</p></div></div></aside>
    </div>
  </div>
}
