import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return <div className="max-w-2xl mx-auto px-4 py-24 text-center"><h1 className="text-6xl font-extrabold text-gray-200">404</h1><p className="text-sm text-gray-500 mt-4">요청한 페이지가 존재하지 않습니다</p><Link to="/" className="inline-flex items-center gap-2 mt-8 px-6 py-3 bg-sf-black text-white font-semibold text-sm"><i className="ri-home-line" /> 홈으로 돌아가기</Link></div>
}
