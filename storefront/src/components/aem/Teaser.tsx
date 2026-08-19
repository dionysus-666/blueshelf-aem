import Link from 'next/link';
import { toRoute } from '@/lib/aem';
export function Teaser(p: { title?: string; description?: string; image?: string; link?: string; linkText?: string; empty?: boolean }) {
  if (p.empty) return null;
  return (
    <div className="cmp-teaser">
      {p.image && <img className="cmp-teaser__image" src={p.image} alt={p.title || ''} />}
      <div className="cmp-teaser__content">
        <h3 className="cmp-teaser__title">{p.title}</h3>
        {p.description && <div className="cmp-teaser__description" dangerouslySetInnerHTML={{ __html: p.description }} />}
        {p.link && <Link className="cmp-teaser__action" href={toRoute(p.link)}>{p.linkText || 'Learn more'}</Link>}
      </div>
    </div>
  );
}
