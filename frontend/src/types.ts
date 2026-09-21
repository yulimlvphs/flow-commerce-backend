export interface Member {
  id: number
  email: string
  role: 'USER' | 'ADMIN'
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
}

export interface Product {
  id: number
  name: string
  price: number
  stock: number
  description: string
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
