/** Rich text authored in AEM. It was sanitized by HTL's context='html' on the AEM side; here we trust our own CMS. */
export function Text(p: { text?: string }) {
  return p.text ? <div className="text" dangerouslySetInnerHTML={{ __html: p.text }} /> : null;
}
