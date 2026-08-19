import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Two clientlibs out of one build (AEM archetype: clientlib-site + clientlib-dependencies, via aem-clientlib-generator):
 *   src/main.tsx     -> /apps/blueshelf/clientlibs/clientlib-author/{js/author.js,css/author.css}
 *   src/site/site.ts -> /apps/blueshelf/clientlibs/clientlib-site/{js/site.js,css/site.css}
 * The folders' .content.xml (cq:ClientLibraryFolder) + css.txt/js.txt live in ui.apps and are NOT generated.
 */
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/blueshelf/clientlibs',
    emptyOutDir: false,
    sourcemap: false,
    rollupOptions: {
      input: { author: 'src/main.tsx', site: 'src/site/site.ts' },
      output: {
        entryFileNames: 'clientlib-[name]/js/[name].js',
        chunkFileNames: 'clientlib-author/js/[name].js',
        assetFileNames: (a) => (a.name?.endsWith('.css') ? 'clientlib-[name]/css/[name].css' : 'clientlib-author/assets/[name][extname]'),
      },
    },
  },
  server: {
    proxy: { '^/(content|apps|conf|bin|system|j_security_check|editor|sites)': { target: 'http://localhost:4502', changeOrigin: true } },
  },
});
