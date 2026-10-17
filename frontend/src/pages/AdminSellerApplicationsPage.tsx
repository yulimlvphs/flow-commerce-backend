import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, apiFetch } from '../lib/api'
import { useUi } from '../context/UiContext'
import type { PageResponse, SellerApplication } from '../types'

const statusLabels: Record<SellerApplication['status'], string> = {
  PENDING: '심사 대기', APPROVED: '승인 완료', REJECTED: '거절'
}

export function AdminSellerApplicationsPage() {
  const { showConfirm, showToast } = useUi()
  const [status, setStatus] = useState<SellerApplication['status']>('PENDING')
  const [page, setPage] = useState(0)
  const [applications, setApplications] = useState<SellerApplication[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const [rejecting, setRejecting] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const request = useRef(0)
  const processing = useRef(false)

  const load = useCallback(async () => {
    const current = ++request.current
    setLoading(true); setError('')
    try {
      const result = await apiFetch<PageResponse<SellerApplication>>(`/api/admin/seller-applications?status=${status}&page=${page}&size=20&sort=id,desc`)
      if (current !== request.current) return
      if (!result || !Array.isArray(result.content)) throw new Error('신청 목록 응답이 올바르지 않습니다.')
      if (page > 0 && page >= result.totalPages) { setPage(Math.max(0, result.totalPages - 1)); return }
      setApplications(result.content); setTotalPages(result.totalPages); setTotalElements(result.totalElements)
    } catch (e) { if (current === request.current) setError((e as Error).message) }
    finally { if (current === request.current) setLoading(false) }
  }, [status, page])

  useEffect(() => {
    document.title = '판매자 신청 관리 | SpartaFashion'
    setRejecting(null); setReason(''); setActionError('')
    void load()
    return () => { request.current++ }
  }, [load])

  const review = async (application: SellerApplication, decision: 'approve' | 'reject') => {
    if (processing.current || application.status !== 'PENDING') return
    const trimmedReason = reason.trim()
    if (decision === 'reject' && (!trimmedReason || trimmedReason.length > 500)) {
      setActionError('거절 사유를 1~500자로 입력하세요.'); return
    }
    processing.current = true
    setBusy(application.id); setActionError('')
    try {
      await apiFetch<SellerApplication>(`/api/admin/seller-applications/${application.id}/${decision}`, {
        method: 'POST', ...(decision === 'reject' ? { body: JSON.stringify({ reason: trimmedReason }) } : {})
      })
      setRejecting(null); setReason('')
      showToast(decision === 'approve' ? '승인했습니다. 신청자에게 SELLER 권한이 부여되었습니다.' : '판매자 신청을 거절했습니다.')
      await load()
    } catch (e) {
      setActionError((e as Error).message)
      // 다른 관리자가 이미 처리한 신청이라면 최신 목록으로 갱신한다.
      if (e instanceof ApiError && (e.status === 409 || e.status === 404)) {
        setRejecting(null); setReason(''); await load()
      }
    } finally { processing.current = false; setBusy(null) }
  }
  const approve = (application: SellerApplication) => showConfirm(
    `${application.businessName}의 판매자 신청을 승인할까요? 승인하면 신청자에게 SELLER 권한이 부여됩니다.`,
    () => { void review(application, 'approve') }
  )
  const reject = (event: FormEvent, application: SellerApplication) => {
    event.preventDefault()
    void review(application, 'reject')
  }

  return <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12">
    <div className="flex flex-wrap justify-between gap-4 mb-8">
      <div><p className="text-xs font-bold text-gray-500 mb-2">ADMIN CENTER</p><h1 className="text-3xl font-black">판매자 신청 관리</h1><p className="text-sm text-gray-500 mt-3">사업자 정보를 확인하고 판매자 전환 신청을 승인하거나 거절하세요.</p></div>
      <button type="button" disabled={loading || busy !== null} onClick={() => { setActionError(''); void load() }} className="border rounded px-4 py-2 h-fit disabled:opacity-50">목록 새로고침</button>
    </div>
    <div className="flex items-center justify-between gap-4 mb-6">
      <label className="text-sm font-medium">신청 상태 <select aria-label="신청 상태 필터" disabled={loading || busy !== null} value={status} onChange={e => { setStatus(e.target.value as SellerApplication['status']); setPage(0) }} className="border rounded p-2 ml-2"><option value="PENDING">심사 대기</option><option value="APPROVED">승인 완료</option><option value="REJECTED">거절</option></select></label>
      <p className="text-sm text-gray-500">총 {totalElements}건</p>
    </div>
    {actionError && <p role="alert" className="text-red-600 border border-red-200 rounded p-4 mb-5">{actionError}</p>}
    {loading ? <p role="status" className="py-10 text-center">신청 목록을 불러오는 중…</p> : error ? <div role="alert" className="text-red-600 py-8"><p>{error}</p><button type="button" onClick={() => void load()} className="underline mt-3">다시 시도</button></div> : <>
      {applications.length === 0 && <p className="py-16 text-center text-gray-500">{statusLabels[status]} 상태의 신청이 없습니다.</p>}
      <div className="space-y-5">{applications.map(application => <article key={application.id} className="border rounded-xl p-5 sm:p-6">
        <div className="flex flex-wrap justify-between gap-3"><h2 className="text-lg font-bold">{application.businessName}</h2><span className="text-sm font-bold bg-gray-100 rounded-full px-3 py-1">{statusLabels[application.status]}</span></div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-3 mt-5 text-sm"><dt>신청 번호</dt><dd>{application.id}</dd><dt>신청자 ID</dt><dd>{application.userId}</dd><dt>사업자등록번호</dt><dd>{application.businessRegistrationNumber}</dd><dt>신청 일시</dt><dd>{application.appliedAt.replace('T', ' ')}</dd><dt>심사 일시</dt><dd>{application.reviewedAt?.replace('T', ' ') || '미처리'}</dd></dl>
        {application.status === 'REJECTED' && <p className="text-sm text-red-600 mt-5 whitespace-pre-wrap">거절 사유: {application.rejectReason || '사유가 등록되지 않았습니다.'}</p>}
        {application.status === 'PENDING' && <>
          <div className="flex gap-3 mt-6"><button type="button" disabled={busy !== null} onClick={() => approve(application)} className="bg-black text-white rounded px-5 py-2 disabled:opacity-50">{busy === application.id ? '처리 중…' : '승인'}</button><button type="button" disabled={busy !== null} onClick={() => { setRejecting(application.id); setReason(''); setActionError('') }} className="border border-red-300 text-red-600 rounded px-5 py-2 disabled:opacity-50">거절</button></div>
          {rejecting === application.id && <form onSubmit={e => reject(e, application)} className="mt-5 border-t pt-5">
            <label className="block text-sm font-medium">거절 사유<textarea autoFocus required maxLength={500} disabled={busy !== null} value={reason} onChange={e => setReason(e.target.value)} className="block border rounded p-3 mt-2 w-full" placeholder="신청자에게 전달할 보완 사유를 입력하세요." /></label>
            <p className="text-xs text-gray-500 mt-1 text-right">{reason.length}/500</p>
            <div className="flex gap-3 mt-3"><button type="submit" disabled={busy !== null || !reason.trim()} className="bg-red-600 text-white rounded px-4 py-2 disabled:opacity-50">거절 처리</button><button type="button" disabled={busy !== null} onClick={() => { setRejecting(null); setReason('') }} className="border rounded px-4 py-2 disabled:opacity-50">취소</button></div>
          </form>}
        </>}
      </article>)}</div>
      <div className="flex justify-center items-center gap-5 mt-8"><button type="button" disabled={page === 0 || busy !== null} onClick={() => setPage(page - 1)} className="disabled:opacity-30">이전</button><span>{totalPages ? page + 1 : 0} / {totalPages}</span><button type="button" disabled={page + 1 >= totalPages || busy !== null} onClick={() => setPage(page + 1)} className="disabled:opacity-30">다음</button></div>
    </>}
  </div>
}
