import Link from 'next/link';

/**
 * Bridge page: renders content from a REAL AEM as a Cloud Service instance (30-day trial) via a
 * published PERSISTED QUERY — the production AEM-headless pattern:
 *   GET <publish>/graphql/execute.json/<config>/<queryName>     (cacheable, allow-listed)
 * Server component → fetch happens on the server (no CORS), ISR 5 min. If the query isn't published
 * (or the trial expired), the page renders a friendly notice instead of failing the build.
 */
// Preferred: a published persisted query (cacheable GET; set AEM_TRIAL_GRAPHQL_URL when available).
// Fallback: the direct POST endpoint — open on this non-prod trial; a hardened prod publish would
// block arbitrary POST GraphQL at the CDN/dispatcher and allow persisted queries only.
const PERSISTED_URL = process.env.AEM_TRIAL_GRAPHQL_URL || '';
const POST_ENDPOINT = process.env.AEM_TRIAL_GRAPHQL_ENDPOINT
  || 'https://publish-p153710-e1614654.adobeaemcloud.com/content/_cq_graphql/aem-boilerplate-frescopa/endpoint.json';
const LIST_QUERY = '{ articleList(limit: 20, sort: "title ASC") { items { _path title author } } }';

interface Article { _path: string; title: string; author?: string }

async function fetchArticles(): Promise<{ articles: Article[]; error?: string; via: string }> {
  const attempts: { via: string; run: () => Promise<Response> }[] = [];
  if (PERSISTED_URL) attempts.push({ via: 'persisted query (GET)', run: () => fetch(PERSISTED_URL, { next: { revalidate: 300 } }) });
  attempts.push({
    via: 'direct endpoint (POST)',
    run: () => fetch(POST_ENDPOINT, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ query: LIST_QUERY }), next: { revalidate: 300 } }),
  });
  let error = 'no endpoint configured';
  for (const a of attempts) {
    try {
      const res = await a.run();
      const json = await res.json().catch(() => null);
      const items = json?.data?.articleList?.items;
      if (res.ok && items) return { articles: items, via: a.via };
      error = json?.errors?.[0]?.message || `HTTP ${res.status}`;
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : 'fetch failed';
    }
  }
  return { articles: [], error, via: 'none' };
}

export const metadata = { title: 'Frescopa Articles | BlueShelf' };

export default async function FrescopaPage() {
  const { articles, error, via } = await fetchArticles();
  return (
    <section className="plp">
      <header className="plp__head">
        <h2>Frescopa articles — live from AEM as a Cloud Service</h2>
        <small className="plp__src">source: {via}</small>
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
