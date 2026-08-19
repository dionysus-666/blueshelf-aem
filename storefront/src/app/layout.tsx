import type { Metadata } from 'next';
import Link from 'next/link';
import './globals.css';
import { fetchPage, SITE_ROOT, toRoute } from '@/lib/aem';

export const metadata: Metadata = { title: 'BlueShelf', description: 'Headless storefront on AEM-style content' };

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Navigation is content: read it from the home page model (authors control it via hideInNav / page order)
  const home = await fetchPage(SITE_ROOT).catch(() => null);
  const nav = home?.navigation || [{ title: 'Home', path: SITE_ROOT }];
  return (
    <html lang="en">
      <body className="page">
        <header className="site-header">
          <Link className="logo" href="/">BlueShelf</Link>
          <nav>{nav.map((n) => <Link key={n.path} href={toRoute(n.path)}>{n.title}</Link>)}</nav>
          <span className="headless">headless · Next.js</span>
        </header>
        <main>{children}</main>
        <footer className="site-footer"><small>BlueShelf storefront · content from {process.env.AEM_HOST || 'http://localhost:4503'}</small></footer>
      </body>
    </html>
  );
}
