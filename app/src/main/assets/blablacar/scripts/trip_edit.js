(function() {
  const absolute = (href) => { try { return new URL(href || '', location.href).href; } catch (_) { return href || ''; } };
  const candidates = Array.from(document.querySelectorAll('a[href], [data-href]'))
    .map((node) => absolute(node.getAttribute('href') || node.getAttribute('data-href') || ''))
    .filter(Boolean);
  const option = candidates.find((href) => /\/rides\/offer\/edit\/[^/?#]+\/options\/?(?:$|[?#])/i.test(href)) || '';
  const clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
clone.querySelectorAll('input, textarea').forEach((node) => {
  node.removeAttribute('value');
  node.textContent = '';
});
const html = clone.outerHTML || '';
  return JSON.stringify({
    optionsHref: option,
    pageUrl: location.href || '',
    domHtml: html.slice(0, 350000)
  });
})();
