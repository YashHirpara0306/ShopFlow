export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  currency: string;
  categoryId: string;
  categoryName: string;
  categorySlug: string;
  imageUrl: string | null;
  active: boolean;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  phone: string | null;
  roles: string[];
  status: string;
  createdAt: string;
}

export interface CartLine {
  productId: string;
  name: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Cart {
  userId: string;
  items: CartLine[];
  totalQuantity: number;
  subtotal: number;
  currency: string;
}

export interface OrderItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderHistoryEntry {
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  at: string;
}

export type OrderStatus =
  | "PENDING"
  | "INVENTORY_PENDING"
  | "AWAITING_PAYMENT"
  | "CONFIRMED"
  | "CANCELLED"
  | "REFUNDED"
  | "FAILED";

export interface Order {
  id: string;
  userId: string;
  status: OrderStatus;
  currency: string;
  totalAmount: number;
  cancelReason: string | null;
  items: OrderItem[];
  history: OrderHistoryEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId: string | null;
}
