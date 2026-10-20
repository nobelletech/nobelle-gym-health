import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Relative asset paths so the same build works on localhost:5173 and on a
// GitHub Pages project site (https://<user>.github.io/<repo>/).
export default defineConfig({
  base: "./",
  plugins: [react()],
  server: { port: 5173 },
});
