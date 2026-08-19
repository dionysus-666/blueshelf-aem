/** Server component: a plain GET form to /search — works without JS. */
export function SearchBox(p: { placeholder?: string }) {
  return (
    <form className="search" action="/search" method="get">
      <input className="search__input" type="search" name="q" placeholder={p.placeholder || 'Search products'} required />
      <button className="search__btn" type="submit">Search</button>
    </form>
  );
}
