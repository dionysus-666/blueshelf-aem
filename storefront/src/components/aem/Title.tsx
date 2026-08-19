import { createElement } from 'react';
export function Title(p: { text?: string; type?: string }) {
  if (!p.text) return null;
  const tag = ['h1', 'h2', 'h3', 'h4'].includes(p.type || '') ? (p.type as string) : 'h2';
  return <div className="cmp-title">{createElement(tag, { className: 'cmp-title__text' }, p.text)}</div>;
}
