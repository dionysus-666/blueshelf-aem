import Link from 'next/link';

/**
 * Bridge page: renders content from a REAL AEM as a Cloud Service instance (30-day trial) via a
 * published PERSISTED QUERY — the production AEM-headless pattern:
 *   GET <publish>/graphql/execute.json/<config>/<queryName>     (cacheable, allow-listed)
 * Server component → fetch happens on the server (no CORS), ISR 5 min. If the query isn't published
 * (or the trial expired), the page renders a friendly notice instead of failing the build.
 */
const QUERY_URL = process.env.AEM_TRIAL_GRAPHQL_URL
  || 'https://publish-p153710-e1614654.adobeaemcloud.com/graphql/execute.json/frescopa/AllArticles';

interface Article { _path: string; title: string; author?: string }

async function fetchArticles(): Promise<{ articles: Article[]; error?: string }> {
  try {
    const res = await fetch(QUERY_URL, { next: { revalidate: 300 }, headers: { Accept: 'application/json' } });
    const json = await res.json().catch(() => null);
    const items = json?.data?.articleList?.items;
    if (!res.ok || !items) return { articles: [], error: json?.errors?.[0]?.message || `HTTP ${res.status}` };
    return { articles: items };
  } catch (e: unknown) {
    return { articles: [], error: e instanceof Error ? e.message : 'fetch failed' };
  }
}

export const metadata = { title: 'Frescopa Articles | BlueShelf' };

export default async function FrescopaPage() {
  const { articles, error } = await fetchArticles();
  return (
    <section className="plp">
      <header className="plp__head">
        <h2>Frescopa articles — live from AEM as a Cloud Service</h2>
        <small className="plp__src">source: persisted GraphQL query</small>
      </header>
      <p>
        This content is authored as <b>Content Fragments</b> in a real AEMaaCS instance and delivered through a
        published persisted query — no backend code, cacheable GETs. (Everything else on this site comes from the
        Sling-based publish tier.)
      </p>
      {error && (
        <div className="plp__unavailable">
          AEM trial content unavailable right now (<code>{error}</code>). The persisted query <code>AllArticles</code> may
          not be published yet, or the 30-day trial ended.
        </div>
      )}
      {!error && (
        <ul className="plp__grid">
          {articles.map((a) => (
            <li className="card" key={a._path}>
              <span className="card__brand">{a.author || 'Frescopa'}</span>
              <span className="card__name">{a.title}</span>
              <small className="muted">{a._path.split('/').pop()}</small>
            </li>
          ))}
        </ul>
      )}
      <p><Link href="/">← back to BlueShelf</Link></p>
    </section>
  );
}
