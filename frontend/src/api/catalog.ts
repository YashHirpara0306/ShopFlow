import { api } from "./client";
import type { Category, Order, Page, Product } from "./types";

export interface ProductQuery {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  q?: string;
}

export async function listProducts(query: ProductQuery = {}): Promise<Page<Product>> {
  const params: Record<string, string | number> = {};
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== "" && v !== null) params[k] = v as string | number;
  }
  const { data } = await api.get<Page<Product>>("/api/products", { params });
  return data;
}

export async function getProduct(id: string): Promise<Product> {
  const { data } = await api.get<Product>(`/api/products/${id}`);
  return data;
}

export async function listCategories(): Promise<Category[]> {
  const { data } = await api.get<Category[]>("/api/categories");
  return data;
}

// ---- cart (server) ----
export async function serverCartClear(): Promise<void> {
  await api.delete("/api/cart");
}
export async function serverCartAdd(productId: string, quantity: number): Promise<void> {
  await api.post("/api/cart/items", { productId, quantity });
}

// ---- orders ----
export async function createOrder(idempotencyKey: string): Promise<Order> {
  const { data } = await api.post<Order>("/api/orders", null, {
    headers: { "Idempotency-Key": idempotencyKey },
  });
  return data;
}
export async function getOrder(id: string): Promise<Order> {
  const { data } = await api.get<Order>(`/api/orders/${id}`);
  return data;
}
export async function listMyOrders(page = 0, size = 20): Promise<Page<Order>> {
  const { data } = await api.get<Page<Order>>("/api/orders", { params: { page, size } });
  return data;
}
