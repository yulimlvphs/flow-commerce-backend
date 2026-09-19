import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { apiFetch } from '../lib/api'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import type { CartItem } from '../types'

const notices = [
  '신규 회원가입 시 10% 할인 쿠폰 즉시 지급',
  '3만원 이상 구매 시 무료배송',
  '2026 S/S 신상품 입고',
  '평일 오후 2시 이전 주문 당일 출고',
  '7일 이내 무료 교환·반품',
  '카드 결제 시 최대 12개월 무이자 할부'
]

export function Layout() {
  const { member, isLoggedIn, logout } = useAuth()
  const { showToast } = useUi()
  const navigate = useNavigate()
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [cartCount, setCartCount] = useState(0)
  const [scrolled, setScrolled] = useState(false)
  const userMenuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10)
    const onClick = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) setUserMenuOpen(false)
    }
    window.addEventListener('scroll', onScroll)
    document.addEventListener('mousedown', onClick)
    return () => {
      window.removeEventListener('scroll', onScroll)
      document.removeEventListener('mousedown', onClick)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      setCartCount(0)
      return
    }
    apiFetch<CartItem[]>('/api/cart/items')
      .then((items) => setCartCount(items.length))
      .catch(() => undefined)

    const update = (event: Event) => {
      const detail = (event as CustomEvent<number>).detail
      if (typeof detail === 'number') setCartCount(detail)
      else apiFetch<CartItem[]>('/api/cart/items').then((items) => setCartCount(items.length)).catch(() => undefined)
    }
    window.addEventListener('cart-updated', update)
    return () => window.removeEventListener('cart-updated', update)
  }, [isLoggedIn])

  const handleLogout = () => {
    logout()
    setUserMenuOpen(false)
    showToast('로그아웃 되었습니다', 'info')
    navigate('/')
  }

  return (
    <div className="font-sans bg-white text-sf-black min-h-screen flex flex-col">
      <div className="bg-sf-black text-white py-2 overflow-hidden">
        <div className="flex animate-marquee whitespace-nowrap">
          {notices.map((notice) => <span key={notice} className="text-[11px] font-light tracking-wider px-8">{notice}</span>)}
        </div>
      </div>

      <header className={`sf-header sticky top-0 z-50 bg-white border-b border-gray-100 ${scrolled ? 'scrolled' : ''}`}>
        <div className="max-w-[1280px] mx-auto px-4 sm:px-6">
          <div className="flex items-center justify-between h-16">
            <Link to="/" className="flex items-center gap-1 group" aria-label="SpartaFashion 홈">
              <span className="text-xl font-light tracking-wider text-sf-black">SPARTA</span>
              <span className="text-xl font-black tracking-wider text-sf-black">FASHION</span>
            </Link>
            <nav className="hidden md:flex items-center gap-12" />
            <div className="flex items-center gap-4">
              <button
                onClick={() => isLoggedIn ? navigate('/cart') : (showToast('로그인이 필요합니다', 'warning'), navigate('/login'))}
                className="relative text-sf-black hover:text-sf-accent transition-colors"
                aria-label="장바구니"
              >
                <i className="ri-shopping-bag-line text-[20px]" aria-hidden="true" />
                {cartCount > 0 && <span className="absolute -top-2 -right-2 w-5 h-5 bg-sf-accent text-white text-[10px] font-bold rounded-full flex items-center justify-center badge-pulse">{cartCount}</span>}
              </button>

              {isLoggedIn ? (
                <div className="relative" ref={userMenuRef}>
                  <button onClick={() => setUserMenuOpen((open) => !open)} className="text-sf-black hover:text-sf-accent transition-colors" aria-label="사용자 메뉴" aria-expanded={userMenuOpen}>
                    <i className="ri-user-line text-[20px]" aria-hidden="true" />
                  </button>
                  {userMenuOpen && (
                    <div className="absolute right-0 mt-3 w-56 bg-white rounded-lg py-1 z-50 border border-gray-100 shadow-lg">
                      <div className="px-4 py-3 border-b border-gray-100">
                        <p className="text-sm font-semibold text-sf-black">{member?.email ?? '사용자'}</p>
                        <p className="text-xs text-gray-500 mt-0.5">{member?.role === 'ADMIN' ? '관리자' : '일반 회원'}</p>
                      </div>
                      <Link onClick={() => setUserMenuOpen(false)} to="/orders" className="flex items-center gap-2 px-4 py-2.5 text-sm text-gray-700 hover:bg-sf-cream transition-colors">
                        <i className="ri-file-list-3-line text-gray-400" aria-hidden="true" /> 주문내역
                      </Link>
                      <button onClick={handleLogout} className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-gray-600 hover:bg-sf-cream transition-colors">
                        <i className="ri-logout-box-r-line text-gray-400" aria-hidden="true" /> 로그아웃
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <Link to="/login" className="text-sf-black hover:text-sf-accent transition-colors" aria-label="로그인">
                  <i className="ri-user-line text-[20px]" aria-hidden="true" />
                </Link>
              )}

              <button onClick={() => setMobileMenuOpen((open) => !open)} className="md:hidden text-sf-black hover:text-sf-accent transition-colors" aria-label="모바일 메뉴" aria-expanded={mobileMenuOpen}>
                <i className="ri-menu-line text-[20px]" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
        {mobileMenuOpen && <div className="md:hidden border-t border-gray-100 bg-white"><div className="max-w-[1280px] mx-auto px-4 py-3 space-y-1" /></div>}
      </header>

      <main className="flex-1"><Outlet /></main>

      <footer className="bg-sf-black text-white mt-auto border-t border-gray-900">
        <div className="max-w-[1280px] mx-auto px-4 sm:px-6 py-16">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-12 mb-12">
            <div>
              <div className="flex items-center gap-1 mb-6"><span className="text-lg font-light tracking-wider">SPARTA</span><span className="text-lg font-black tracking-wider">FASHION</span></div>
              <p className="text-sm font-light text-gray-300 leading-relaxed">트렌디한 패션을 합리적인 가격에.<br />당신의 스타일을 완성하세요.</p>
            </div>
            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-widest mb-4">고객센터</h4>
              <ul className="space-y-2.5 text-sm font-light text-gray-400"><li>평일 10:00 - 18:00</li><li>점심 12:00 - 13:00</li><li>주말/공휴일 휴무</li></ul>
            </div>
            <div>
              <h4 className="text-xs font-bold text-white uppercase tracking-widest mb-4">이용안내</h4>
              <ul className="space-y-2.5 text-sm font-light text-gray-400"><li><a href="#" className="hover:text-white transition-colors">이용약관</a></li><li><a href="#" className="hover:text-white transition-colors">개인정보처리방침</a></li><li><a href="#" className="hover:text-white transition-colors">배송/교환/반품</a></li></ul>
            </div>
          </div>
          <div className="border-t border-gray-800 pt-8"><p className="text-xs font-light text-gray-500">© 2026 SpartaFashion. All rights reserved.</p></div>
        </div>
      </footer>
    </div>
  )
}
