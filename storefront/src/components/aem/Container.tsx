import type { AemContainer } from '@/lib/aem';
import { AemComponent } from './mapping';

/** Renders children in :itemsOrder — the storefront's "parsys". */
export function Container(props: AemContainer) {
  const order = props[':itemsOrder'] || [];
  return <div className="container">{order.map((name) => <AemComponent key={name} item={props[':items'][name]} />)}</div>;
}
