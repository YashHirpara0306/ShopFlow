import { api } from "./client";
import type { Category, Product } from "./types";

export async function createCategory(name: string): Promise<Category> {
  const { data } = await api.post<Category>("/api/categories", { name });
  return data;
}

export interface NewProduct {
  sku: string;
  name: string;
  description?: string;
  price: number;
  categoryId: string;
  imageUrl?: string;
}

export async function createProduct(body: NewProduct): Promise<Product> {
  const { data } = await api.post<Product>("/api/products", body);
  return data;
}

export async function setStock(productId: string, quantity: number): Promise<{ total: number; available: number; reserved: number }> {
  const { data } = await api.post("/api/inventory/stock", { productId, quantity });
  return data;
}

export async function getStock(productId: string): Promise<{ productId: string; total: number; available: number; reserved: number }> {
  const { data } = await api.get(`/api/inventory/${productId}`);
  return data;
}
