import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useUi } from '../context/UiContext'
import { ApiError, apiFetch, refreshAccessToken } from '../lib/api'
import type { SellerApplication } from '../types'

const statusLabels: Record<SellerApplication['status'], string> = {
  PENDING: '심사 대기', APPROVED: '승인 완료', REJECTED: '거절'
}

export function SellerApplicationPage({ showStatus = false }: { showStatus?: boolean }) {
  const { user, saveAuth } = useAuth()
  const { showToast } = useUi()
  const [application, setApplication] = useState<SellerApplication | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({ businessName: '', businessRegistrationNumber: '' })

  useEffect(() => {
    let active = true
    document.title = `${showStatus ? '내 신청 상태' : '판매자 전환 신청'} | SpartaFashion`
    setLoading(true); setError(''); setApplication(null)
    apiFetch<SellerApplication>('/api/sellers/apply/me')
      .then(result => { if (active) setApplication(result) })
      .catch((e: Error) => {
        if (!active) return
        if (e instanceof ApiError && e.status === 404 && e.code === 'SELLER_003') return
        setError(e.message)
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [showStatus, retry, user?.id])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy || loading || error || user?.role !== 'USER') return
    const businessName = form.businessName.trim()
    if (!businessName || !/^\d{3}-\d{2}-\d{5}$/.test(form.businessRegistrationNumber)) {
      showToast('사업자명과 사업자등록번호 형식을 확인하세요.', 'warning'); return
    }
    setBusy(true)
    try {
      const result = await apiFetch<SellerApplication>('/api/sellers/apply', {
        method: 'POST', body: JSON.stringify({ businessName, businessRegistrationNumber: form.businessRegistrationNumber })
      })
      setApplication(result); setForm({ businessName: '', businessRegistrationNumber: '' })
      showToast('판매자 전환 신청이 접수되었습니다.')
    } catch (e) { showToast((e as Error).message, 'danger') }
    finally { setBusy(false) }
  }
  const updateRole = async () => {
    setBusy(true)
    try { saveAuth(await refreshAccessToken()); showToast('현재 권한을 반영했습니다.') }
    catch (e) { showToast((e as Error).message, 'danger') }
    finally { setBusy(false) }
  }
  const canApply = user?.role === 'USER' && (!application || application.status === 'REJECTED')

  return <div className="max-w-3xl mx-auto px-4 sm:px-6 py-12">
    <h1 className="text-3xl font-black">{showStatus ? '내 판매자 신청 상태' : '판매자 전환 신청'}</h1>
    <p className="text-sm text-gray-500 mt-3 mb-8">사업자 정보를 제출하면 관리자가 심사 후 판매자 권한을 부여합니다.</p>
    <nav className="flex gap-4 border-b pb-4 mb-8" aria-label="판매자 신청 메뉴">
      <Link to="/seller/apply" aria-current={!showStatus ? 'page' : undefined} className={!showStatus ? 'font-bold' : 'text-gray-500'}>신청하기</Link>
      <Link to="/seller/apply/me" aria-current={showStatus ? 'page' : undefined} className={showStatus ? 'font-bold' : 'text-gray-500'}>내 신청 상태</Link>
    </nav>
    {loading ? <p role="status">신청 내역을 불러오는 중…</p> : error ? <div role="alert" className="text-red-600"><p>{error}</p><button onClick={() => setRetry(retry + 1)} className="underline mt-3">다시 시도</button></div> : <>
      {application ? <section className="border rounded-xl p-6 mb-8">
        <div className="flex items-center justify-between gap-4"><h2 className="text-lg font-bold">최신 신청 내역</h2><span className="text-sm font-bold bg-gray-100 rounded-full px-3 py-1">{statusLabels[application.status]}</span></div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-3 text-sm mt-6"><dt>신청 번호</dt><dd>{application.id}</dd><dt>사업자명</dt><dd>{application.businessName}</dd><dt>사업자등록번호</dt><dd>{application.businessRegistrationNumber}</dd><dt>신청 일시</dt><dd>{application.appliedAt.replace('T', ' ')}</dd><dt>심사 일시</dt><dd>{application.reviewedAt?.replace('T', ' ') || '심사 대기 중'}</dd></dl>
        {application.status === 'REJECTED' && <p className="text-red-600 text-sm mt-5 whitespace-pre-wrap">거절 사유: {application.rejectReason || '사유가 등록되지 않았습니다.'}</p>}
        {application.status === 'PENDING' && <p className="text-gray-500 text-sm mt-5">현재 심사 중입니다. 심사가 끝날 때까지 추가 신청할 수 없습니다.</p>}
        {application.status === 'APPROVED' && <div className="mt-5 text-sm"><p>판매자 전환이 승인되었습니다. 상품 관리는 상단 사용자 메뉴에서 이용하세요.</p>{user?.role === 'USER' && <button disabled={busy} onClick={() => void updateRole()} className="border rounded px-4 py-2 mt-3 disabled:opacity-50">판매자 권한 반영</button>}</div>}
      </section> : <p className="text-gray-500 mb-8">아직 판매자 신청 내역이 없습니다.</p>}
      {showStatus && <button type="button" disabled={busy} onClick={() => setRetry(retry + 1)} className="border rounded px-4 py-2 disabled:opacity-50">신청 상태 새로고침</button>}
      {!showStatus && canApply && <form onSubmit={submit} className="border rounded-xl p-6 space-y-5">
        <h2 className="text-lg font-bold">{application?.status === 'REJECTED' ? '정보 보완 후 재신청' : '사업자 정보'}</h2>
        <fieldset disabled={busy} className="space-y-5 disabled:opacity-50">
          <label className="block text-sm font-medium">사업자명<input required maxLength={100} value={form.businessName} onChange={e => setForm({ ...form, businessName: e.target.value })} className="block border rounded p-3 mt-2 w-full" /></label>
          <label className="block text-sm font-medium">사업자등록번호<input required pattern="[0-9]{3}-[0-9]{2}-[0-9]{5}" maxLength={12} placeholder="123-45-67890" value={form.businessRegistrationNumber} onChange={e => setForm({ ...form, businessRegistrationNumber: e.target.value })} className="block border rounded p-3 mt-2 w-full" /></label>
          <button type="submit" className="bg-black text-white rounded px-5 py-3">{busy ? '신청 중…' : '판매자 전환 신청'}</button>
        </fieldset>
      </form>}
      {!showStatus && user?.role !== 'USER' && <p className="text-sm text-gray-500">판매자 전환 신청은 일반 회원(USER)만 가능합니다.</p>}
      {showStatus && canApply && <Link to="/seller/apply" className="inline-block bg-black text-white rounded px-5 py-2 ml-3">{application ? '재신청하기' : '판매자 신청하기'}</Link>}
    </>}
  </div>
}
