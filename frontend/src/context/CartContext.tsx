import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Product } from "../api/types";

export interface CartLine {
  productId: string;
  name: string;
  price: number;
  currency: string;
  imageUrl: string | null;
  quantity: number;
}

interface CartValue {
  lines: CartLine[];
  count: number;
  subtotal: number;
  currency: string;
  add: (p: Product, qty?: number) => void;
  setQty: (productId: string, qty: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
}

const KEY = "shopflow.cart";
const CartCtx = createContext<CartValue>(null as never);

function load(): CartLine[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>(load);

  useEffect(() => {
    localStorage.setItem(KEY, JSON.stringify(lines));
  }, [lines]);

  const add = useCallback((p: Product, qty = 1) => {
    setLines((cur) => {
      const i = cur.findIndex((l) => l.productId === p.id);
      if (i >= 0) {
        const next = [...cur];
        next[i] = { ...next[i], quantity: Math.min(99, next[i].quantity + qty) };
        return next;
      }
      return [
        ...cur,
        { productId: p.id, name: p.name, price: p.price, currency: p.currency, imageUrl: p.imageUrl, quantity: qty },
      ];
    });
  }, []);

  const setQty = useCallback((productId: string, qty: number) => {
    setLines((cur) =>
      qty <= 0
        ? cur.filter((l) => l.productId !== productId)
        : cur.map((l) => (l.productId === productId ? { ...l, quantity: Math.min(99, qty) } : l)),
    );
  }, []);

  const remove = useCallback((productId: string) => {
    setLines((cur) => cur.filter((l) => l.productId !== productId));
  }, []);

  const clear = useCallback(() => setLines([]), []);

  const value = useMemo<CartValue>(() => {
    const count = lines.reduce((n, l) => n + l.quantity, 0);
    const subtotal = lines.reduce((n, l) => n + l.price * l.quantity, 0);
    return { lines, count, subtotal, currency: lines[0]?.currency ?? "INR", add, setQty, remove, clear };
  }, [lines, add, setQty, remove, clear]);

  return <CartCtx.Provider value={value}>{children}</CartCtx.Provider>;
}

export const useCart = () => useContext(CartCtx);
