import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build straight into the ui.apps clientlib folder with STABLE file names (author.js / author.css),
// because the HTL shells reference them by path. (AEM clientlibs get cache-busting via /etc.clientlibs + "lc-<hash>".)
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/blueshelf/clientlibs/author',
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      input: 'src/main.tsx',
      output: {
        entryFileNames: 'author.js',
        assetFileNames: (a) => (a.name?.endsWith('.css') ? 'author.css' : 'assets/[name][extname]'),
      },
    },
  },
  server: {
    // local dev: `npm run dev` and open http://localhost:5173/?page=/content/blueshelf/us/en — API calls proxy to Sling
    proxy: { '^/(content|apps|conf|bin|system|j_security_check|editor|sites)': { target: 'http://localhost:4502', changeOrigin: true } },
  },
});
