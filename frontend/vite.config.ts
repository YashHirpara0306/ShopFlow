import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In dev, proxy /api to the API Gateway so there is no CORS to deal with.
// For a production build, set VITE_API_BASE to the gateway's origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_GATEWAY_URL || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
