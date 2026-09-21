import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useUi } from '../context/UiContext'
import { apiFetch } from '../lib/api'
import type { Member } from '../types'

export function SignupPage() {
  const { showToast } = useUi()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  useEffect(() => { document.title = '회원가입 | SpartaFashion' }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true)
    try {
      if (new TextEncoder().encode(form.password).length > 72) {
        throw new Error('비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.')
      }
      const member = await apiFetch<Member>('/api/auth/signup', { method: 'POST', body: JSON.stringify(form) })
      showToast('회원가입이 완료되었습니다')
      navigate('/login', { state: { email: member.email } })
    }
    catch (error) { showToast((error as Error).message, 'danger') }
    finally { setLoading(false) }
  }

  const field = (key: keyof typeof form, value: string) => setForm((current) => ({ ...current, [key]: value }))
  return <div className="max-w-md mx-auto px-4 sm:px-6 py-20">
    <div className="text-center mb-12"><h1 className="text-4xl font-light tracking-widest uppercase text-gray-900">회원가입</h1><p className="text-sm text-gray-500 mt-4">새로운 스타일의 시작</p></div>
    <form onSubmit={submit} className="space-y-5">
      <div><label className="block text-xs font-semibold text-gray-700 mb-2 uppercase tracking-widest">이메일</label><input value={form.email} onChange={(e) => field('email', e.target.value)} type="email" maxLength={254} required className="w-full border border-gray-300 px-4 py-3 text-sm" /></div>
      <div><label className="block text-xs font-semibold text-gray-700 mb-2 uppercase tracking-widest">비밀번호</label><input value={form.password} onChange={(e) => field('password', e.target.value)} type="password" minLength={8} required className="w-full border border-gray-300 px-4 py-3 text-sm" /><p className="mt-2 text-xs text-gray-500">8자 이상, UTF-8 기준 72바이트 이하</p></div>
      <button disabled={loading} className="w-full bg-black text-white py-4 text-sm font-light uppercase tracking-widest hover:bg-gray-900 flex justify-center">{loading ? <span className="spinner" /> : '가입하기'}</button>
    </form>
    <p className="text-center text-sm text-gray-500 mt-8">이미 회원이신가요? <Link to="/login" className="font-semibold text-black hover:underline">로그인</Link></p>
  </div>
}
