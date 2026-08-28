(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const candidates = Array.from(document.querySelectorAll(
    '[data-testid*="review-card"], [data-testid*="review-item"], [data-testid*="rating-card"], article[class*="review" i], li[class*="review" i]'
  ));
  const reviews = candidates.map((root) => {
    const authorNode = root.querySelector('[data-testid*="name"], strong, h3, h4');
    const dateNode = root.querySelector('time, [data-testid*="date"]');
    const textNode = root.querySelector('[data-testid*="comment"], [data-testid*="content"], p');
    const ratingNode = root.querySelector('[data-testid*="rating"], [aria-label*="estrela" i], [aria-label*="nota" i]');
    const ratingText = clean(ratingNode && (ratingNode.innerText || ratingNode.getAttribute('aria-label')));
    const rating = (ratingText.match(/\b([0-5](?:[.,]\d{1,2})?)\b/) || [])[1] || '';
    return {
      author: clean(authorNode && authorNode.innerText).slice(0, 120),
      rating: rating.replace(',', '.').slice(0, 20),
      dateLabel: clean(dateNode && (dateNode.innerText || dateNode.getAttribute('datetime'))).slice(0, 80),
      text: clean(textNode && textNode.innerText).slice(0, 600)
    };
  }).filter((item) => item.author || item.text);
  const raw = [location.href || '', document.documentElement ? (document.documentElement.outerHTML || '') : ''].join('\n');
  const observedUuids = Array.from(new Set(
    (raw.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig) || [])
      .map((value) => value.toLowerCase())
  ));
  const scrollY = Math.max(0, Math.round(window.scrollY || 0));
  const scrollHeight = Math.max(0, Math.round(document.documentElement.scrollHeight || document.body.scrollHeight || 0));
  const viewportHeight = Math.max(1, Math.round(window.innerHeight || document.documentElement.clientHeight || 1));
  const atBottom = scrollY + viewportHeight >= scrollHeight - 24;
  const clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
clone.querySelectorAll('input, textarea').forEach((node) => {
  node.removeAttribute('value');
  node.textContent = '';
});
const html = clone.outerHTML || '';
  return JSON.stringify({
    observedUuids,
    reviews: reviews.slice(0, 60),
    scrollY,
    scrollHeight,
    viewportHeight,
    atBottom,
    domHtml: html.slice(0, 350000)
  });
})();
