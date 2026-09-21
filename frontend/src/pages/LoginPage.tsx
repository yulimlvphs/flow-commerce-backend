import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import type { TokenResponse } from '../types'

export function LoginPage() {
  const { isLoggedIn, isAuthReady, saveAuth } = useAuth()
  const { showToast } = useUi()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState(() => (location.state as { email?: string } | null)?.email ?? '')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => { document.title = '로그인 | SpartaFashion'; if (isLoggedIn) navigate('/', { replace: true }) }, [isLoggedIn, navigate])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true)
    try {
      if (new TextEncoder().encode(password).length > 72) {
        throw new Error('비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.')
      }
      const data = await apiFetch<TokenResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
      saveAuth(data); showToast('로그인되었습니다')
      navigate((location.state as { from?: string } | null)?.from || '/')
    } catch (error) { showToast((error as Error).message, 'danger') }
    finally { setLoading(false) }
  }

  if (!isAuthReady) return <Loading />

  return <div className="max-w-md mx-auto px-4 sm:px-6 py-20">
    <div className="text-center mb-12"><h1 className="text-4xl font-light tracking-widest uppercase text-gray-900">로그인</h1><p className="text-sm text-gray-500 mt-4">SpartaFashion에 오신 것을 환영합니다</p></div>
    <form onSubmit={submit} className="space-y-6">
      <div><label className="block text-xs font-semibold text-gray-700 mb-2 uppercase tracking-widest">이메일</label><input value={email} onChange={(e) => setEmail(e.target.value)} type="email" maxLength={254} required className="w-full border border-gray-300 px-4 py-3 text-sm" placeholder="email@example.com" /></div>
      <div><label className="block text-xs font-semibold text-gray-700 mb-2 uppercase tracking-widest">비밀번호</label><input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required className="w-full border border-gray-300 px-4 py-3 text-sm" placeholder="비밀번호" /></div>
      <button disabled={loading} className="w-full bg-black text-white py-4 text-sm font-light uppercase tracking-widest hover:bg-gray-900 flex justify-center">{loading ? <span className="spinner" /> : '로그인'}</button>
    </form>
    <button type="button" onClick={() => { setEmail('member@example.com'); setPassword('test-password-1234') }} className="w-full mt-4 border border-gray-200 py-3 text-xs text-gray-500 hover:bg-gray-50">테스트 계정 입력</button>
    <p className="text-center text-sm text-gray-500 mt-8">아직 회원이 아니신가요? <Link to="/signup" className="font-semibold text-black hover:underline">회원가입</Link></p>
  </div>
}
