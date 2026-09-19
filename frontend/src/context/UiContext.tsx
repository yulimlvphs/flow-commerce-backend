import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type ToastType = 'success' | 'danger' | 'warning' | 'info'
type Toast = { id: number; message: string; type: ToastType }
type ConfirmState = { message: string; onConfirm: () => void } | null

type UiContextValue = {
  showToast: (message: string, type?: ToastType) => void
  showConfirm: (message: string, onConfirm: () => void) => void
}

const UiContext = createContext<UiContextValue | null>(null)

const toastColors: Record<ToastType, string> = {
  success: 'bg-sf-black',
  danger: 'bg-red-600',
  warning: 'bg-amber-500',
  info: 'bg-gray-700'
}

const toastIcons: Record<ToastType, string> = {
  success: 'ri-check-line',
  danger: 'ri-error-warning-line',
  warning: 'ri-alert-line',
  info: 'ri-information-line'
}

export function UiProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const [confirm, setConfirm] = useState<ConfirmState>(null)

  const showToast = useCallback((message: string, type: ToastType = 'success') => {
    const id = Date.now() + Math.random()
    setToasts((current) => [...current, { id, message, type }])
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 2500)
  }, [])

  const showConfirm = useCallback((message: string, onConfirm: () => void) => {
    setConfirm({ message, onConfirm })
  }, [])

  const value = useMemo(() => ({ showToast, showConfirm }), [showToast, showConfirm])

  return (
    <UiContext.Provider value={value}>
      {children}
      <div className="fixed top-20 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
        {toasts.map((toast) => (
          <div key={toast.id} className={`pointer-events-auto flex items-center gap-3 px-5 py-3 rounded-lg text-white text-sm font-medium ${toastColors[toast.type]} animate-slide-in`}>
            <i className={toastIcons[toast.type]} aria-hidden="true" />
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
      {confirm && (
        <div className="fixed inset-0 z-[10000] flex items-center justify-center" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
          <button className="absolute inset-0 bg-black/40 backdrop-blur-sm cursor-default" onClick={() => setConfirm(null)} aria-label="닫기" />
          <div className="relative bg-white rounded-lg shadow-2xl max-w-sm w-full mx-4 animate-fade-in overflow-hidden">
            <div className="px-8 pt-8 pb-6">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-full bg-red-50 flex items-center justify-center flex-shrink-0">
                  <i className="ri-error-warning-line text-red-500 text-xl" aria-hidden="true" />
                </div>
                <h3 id="confirm-title" className="text-base font-semibold text-gray-900">확인</h3>
              </div>
              <p className="text-sm text-gray-600 leading-relaxed pl-[52px]">{confirm.message}</p>
            </div>
            <div className="flex border-t border-gray-100">
              <button onClick={() => setConfirm(null)} className="flex-1 px-6 py-4 text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors">취소</button>
              <button onClick={() => { const action = confirm.onConfirm; setConfirm(null); action() }} className="flex-1 px-6 py-4 text-sm font-bold text-white bg-red-600 hover:bg-red-700 transition-colors">확인</button>
            </div>
          </div>
        </div>
      )}
    </UiContext.Provider>
  )
}

export function useUi() {
  const context = useContext(UiContext)
  if (!context) throw new Error('useUi must be used inside UiProvider')
  return context
}
