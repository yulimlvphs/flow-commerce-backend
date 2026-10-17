import type { Deal } from '../types'
export const dealStatusLabel: Record<Deal['displayStatus'], string> = {
  UPCOMING: '오픈 예정', ON_SALE: '판매 중', SOLD_OUT: '품절', CLOSED: '판매 종료'
}
