import type { Metadata } from 'next';
import Link from 'next/link';
import './globals.css';
import { fetchPage, SITE_ROOT, SNAPSHOT, snapshotIndex, toRoute } from '@/lib/aem';

export const metadata: Metadata = { title: 'BlueShelf', description: 'Headless storefront on AEM-style content' };

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Navigation is content: read it from the home page model (authors control it via hideInNav / page order)
  const home = await fetchPage(SITE_ROOT).catch(() => null);
  const nav = home?.navigation || [{ title: 'Home', path: SITE_ROOT }];
  // Footer provenance: a static snapshot (GitHub Pages) says so + when it was crawled; live mode names the publish/dispatcher host.
  const snap = SNAPSHOT ? await snapshotIndex() : null;
  const provenance = SNAPSHOT
    ? `static snapshot of published AEM content${(snap as { generatedAt?: string } | null)?.generatedAt ? ` · crawled ${String((snap as { generatedAt?: string }).generatedAt).slice(0, 10)}` : ''}`
    : `content from ${process.env.AEM_HOST || 'http://localhost:4503'}`;
  return (
    <html lang="en">
      <body className="page">
        <header className="site-header">
          <Link className="logo" href="/">BlueShelf</Link>
          <nav>{nav.map((n) => <Link key={n.path} href={toRoute(n.path)}>{n.title}</Link>)}</nav>
          <span className="headless">headless · Next.js</span>
        </header>
        <main>{children}</main>
        <footer className="site-footer"><small>BlueShelf storefront · {provenance}</small></footer>
      </body>
    </html>
  );
}
