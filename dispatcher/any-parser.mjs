/**
 * Parser for the dispatcher.any format:
 *   /name { /key "value" /sub { ... } "bare" "list" "items" }
 * -> nested JS objects; quoted values in a block without keys become an array-like object {0:"a",1:"b"}.
 */
export function parseAny(text) {
  const src = text.replace(/#[^\n]*/g, '');          // strip comments
  const tokens = src.match(/"[^"]*"|'[^']*'|\{|\}|\/[A-Za-z0-9_.\-]+|[^\s{}"']+/g) || [];
  let i = 0;
  const unq = (t) => (t.startsWith('"') || t.startsWith("'")) ? t.slice(1, -1) : t;
  function block() {
    const obj = {}; let n = 0;
    while (i < tokens.length) {
      const t = tokens[i++];
      if (t === '}') return obj;
      if (t.startsWith('/')) {
        const key = t.substring(1);
        const next = tokens[i];
        if (next === '{') { i++; obj[key] = block(); } else { i++; obj[key] = unq(next); }
      } else if (t === '{') {
        obj[n++] = block();
      } else {
        obj[n++] = unq(t);
      }
    }
    return obj;
  }
  return block();
}
