import type { NextConfig } from 'next';

// STATIC_EXPORT=1 builds a fully static site (GitHub Pages); NEXT_BASE_PATH=/blueshelf-aem when served under a repo path.
const isExport = process.env.STATIC_EXPORT === '1';
const basePath = process.env.NEXT_BASE_PATH || '';
const nextConfig: NextConfig = {
  output: isExport ? 'export' : undefined,
  basePath: basePath || undefined,
  assetPrefix: basePath || undefined,
  trailingSlash: isExport,
  env: { NEXT_PUBLIC_BASE_PATH: basePath },
  images: { unoptimized: true, remotePatterns: [{ protocol: 'https', hostname: 'picsum.photos' }] },
};
export default nextConfig;
