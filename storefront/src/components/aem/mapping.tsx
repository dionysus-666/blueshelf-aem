import type { ComponentType } from 'react';
import type { AemItem } from '@/lib/aem';
import { Hero } from './Hero';
import { Text } from './Text';
import { Title } from './Title';
import { Teaser } from './Teaser';
import { ProductList } from './ProductList';
import { ProductDetail } from './ProductDetail';
import { SearchBox } from './SearchBox';
import { Container } from './Container';

/**
 * `:type` -> React component. This is AEM SPA SDK's MapTo('blueshelf/components/hero')(Hero) — a registry.
 * Unknown types render a small placeholder so authors adding a new component never break the storefront.
 */
const registry: Record<string, ComponentType<any>> = {
  'blueshelf/components/container': Container,
  'blueshelf/components/hero': Hero,
  'blueshelf/components/text': Text,
  'blueshelf/components/title': Title,
  'blueshelf/components/base/title/v1/title': Title,
  'blueshelf/components/teaser': Teaser,
  'blueshelf/components/base/teaser/v1/teaser': Teaser,
  'blueshelf/components/product-list': ProductList,
  'blueshelf/components/product-detail': ProductDetail,
  'blueshelf/components/search-box': SearchBox,
};

export function AemComponent({ item }: { item: AemItem }) {
  const Cmp = registry[item[':type']];
  if (!Cmp) return <div className="unknown">Unmapped component: <code>{item[':type']}</code></div>;
  return <Cmp {...item} />;
}
