import Link from 'next/link';
import { toRoute } from '@/lib/aem';

export function Hero(p: { title?: string; subtitle?: string; ctaLabel?: string; ctaLink?: string; theme?: string; hasCta?: boolean; cssClasses?: string }) {
  if (!p.title) return null;
  // hasCta() is not a bean getter on the Sling side; derive it if the exporter dropped it so the CTA never silently vanishes.
  const hasCta = p.hasCta ?? !!(p.ctaLabel && p.ctaLink);
  return (
    <section className={`hero hero--${p.theme || 'blue'}`}>
      <h1 className="hero__title">{p.title}</h1>
      {p.subtitle && <p className="hero__subtitle">{p.subtitle}</p>}
      {hasCta && p.ctaLink && <Link className="hero__cta" href={toRoute(p.ctaLink)}>{p.ctaLabel}</Link>}
    </section>
  );
}
