import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import type { User } from '../types'

const roleLabels: Record<User['role'], string> = { USER: '일반 회원', SELLER: '판매자', ADMIN: '관리자' }

const notices = ['타임딜 가격과 판매 일정을 확인하세요', '딜별 남은 수량과 1인 구매 한도를 확인하세요']

export function Layout() {
  const { user, isLoggedIn, logout } = useAuth()
  const { showToast } = useUi()
  const navigate = useNavigate()
  const isSeller = isLoggedIn && user?.role === 'SELLER'
  const roleLabel = user ? `${roleLabels[user.role]} (${user.role})` : ''
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
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
            <nav className="hidden md:flex items-center gap-12"><Link to="/" className="text-sm font-bold">타임딜</Link></nav>
            <div className="flex items-center gap-3 sm:gap-4">
              {isSeller && (
                <NavLink
                  to="/seller/products"
                  aria-label="판매자 상품 관리"
                  title="상품 관리"
                  className={({ isActive }) => `inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-bold transition-colors ${isActive ? 'bg-black text-white' : 'bg-gray-100 text-sf-black hover:bg-gray-200'}`}
                >
                  <i className="ri-store-2-line text-xl" aria-hidden="true" />
                  <span className="hidden sm:inline">상품 관리</span>
                </NavLink>
              )}
              {isLoggedIn ? (
                <div className="relative" ref={userMenuRef}>
                  <button onClick={() => setUserMenuOpen((open) => !open)} className="flex items-center gap-2 text-sf-black hover:text-sf-accent transition-colors text-left" aria-label="사용자 메뉴" aria-expanded={userMenuOpen}>
                    <i className="ri-user-line text-[20px]" aria-hidden="true" />
                    <span className="hidden lg:block max-w-48">
                      <span className="block truncate text-xs font-semibold">{user?.email}</span>
                      <span className="block text-[11px] text-gray-500 mt-0.5">{roleLabel}</span>
                    </span>
                  </button>
                  {userMenuOpen && (
                    <div className="absolute right-0 mt-3 w-56 bg-white rounded-lg py-1 z-50 border border-gray-100 shadow-lg">
                      <div className="px-4 py-3 border-b border-gray-100">
                        <p className="text-sm font-semibold text-sf-black">{user?.email ?? '사용자'}</p>
                        <p className="text-xs text-gray-500 mt-0.5">{roleLabel}</p>
                      </div>
                      {isSeller && <Link onClick={() => setUserMenuOpen(false)} to="/seller/products" className="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-sf-cream"><i className="ri-store-2-line" aria-hidden="true" />상품 관리</Link>}
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
        {mobileMenuOpen && <div className="md:hidden border-t border-gray-100 bg-white"><div className="max-w-[1280px] mx-auto px-4 py-3 space-y-3"><Link to="/" onClick={() => setMobileMenuOpen(false)} className="block text-sm">타임딜</Link>{isSeller && <Link to="/seller/products" onClick={() => setMobileMenuOpen(false)} className="block text-sm">상품 관리</Link>}</div></div>}
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
