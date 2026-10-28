import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// The React plugin turns on the automatic JSX runtime. Without it, components
// that don't `import React` crash in the build with "React is not defined".
export default defineConfig({
  plugins: [react()],
})
