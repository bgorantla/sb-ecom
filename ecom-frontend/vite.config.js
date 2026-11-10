import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// The React plugin turns on the automatic JSX runtime. Without it, components
// that don't `import React` crash in the build with "React is not defined".
export default defineConfig({
  plugins: [react()],
  // Vitest reads this. jsdom gives the tests a browser-like page to render components into.
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
})
