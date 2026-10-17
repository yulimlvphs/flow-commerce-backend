export interface User {
  id: number
  email: string
  role: 'USER' | 'SELLER' | 'ADMIN'
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
}

export interface Product {
  id: number
  sellerId: number
  name: string
  originalPrice: number
  description: string | null
  thumbnailUrl: string | null
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

export interface CartItem {
  id: number
  productId: number
  productName: string
  price: number
  quantity: number
  stock: number
}

export interface CheckoutItem {
  productId: number
  productName: string
  price: number
  quantity: number
  subtotal: number
}

export interface CheckoutData {
  items: CheckoutItem[]
  totalPrice: number
}

export interface OrderItem {
  productName: string
  orderPrice: number
  quantity: number
}

export interface Order {
  orderId: number
  paymentId: number
  totalPrice: number
  status: 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED'
  orderName: string
  createdAt: string
  orderItems: OrderItem[]
}

export interface OrderCheckoutResponse {
  orderId: number
  portonePaymentId: string
  totalPrice: number
  orderName: string
  status: string
}

export interface PortOneConfig {
  storeId: string
  channelKey: string
}

export interface PortOnePaymentResponse {
  code?: string
  message?: string
  paymentId: string
}

export interface Deal {
  dealId: number
  productId: number
  productName: string
  productDescription: string | null
  thumbnailUrl: string | null
  originalPrice: number
  dealPrice: number
  discountRate: number
  totalStock: number
  remainingStock: number
  maxPurchasePerUser: number
  openAt: string
  closeAt: string
  holdDurationMinutes: number
  displayStatus: 'UPCOMING' | 'ON_SALE' | 'SOLD_OUT' | 'CLOSED'
  serverNow: string
}

export interface SellerApplication {
  id: number
  userId: number
  businessName: string
  businessRegistrationNumber: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason?: string | null
  appliedAt: string
  reviewedAt?: string | null
}
