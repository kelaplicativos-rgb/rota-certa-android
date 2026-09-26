(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
  const links = Array.from(document.querySelectorAll('a[href]'))
    .map((a) => a.href || '')
    .filter((href) => uuid.test(href) && /(profile|user|member)/i.test(href));
  if (uuid.test(location.href)) links.push(location.href);
  const explicitNameNode = document.querySelector(
    '[data-testid*="profile-name"], [data-testid*="driver-name"], [data-testid*="member-name"], [aria-label*="perfil" i] [data-testid*="name"]'
  );
  const profileAnchor = Array.from(document.querySelectorAll('a[href]')).find((anchor) => {
    const href = anchor.href || '';
    return uuid.test(href) && /(profile|user|member)/i.test(href);
  });
  const profileAnchorImage = profileAnchor && profileAnchor.querySelector('img[alt]');
  const nameCandidate = clean(
    (explicitNameNode && explicitNameNode.innerText) ||
    (profileAnchor && profileAnchor.innerText) ||
    (profileAnchorImage && profileAnchorImage.getAttribute('alt')) ||
    ''
  );
  const photoNode = document.querySelector(
    '[data-testid*="profile"] img[src^="http"], [data-testid*="avatar"] img[src^="http"], img[alt][src^="http"]'
  );
  const aboutNode = document.querySelector(
    '[data-testid*="about"], [data-testid*="bio"], [data-testid*="description"], [aria-label*="apresenta" i]'
  );
  const ratingNode = document.querySelector(
    '[data-testid*="rating"], [aria-label*="nota" i], [aria-label*="avalia" i]'
  );
  const ratingEvidence = clean(ratingNode && (ratingNode.innerText || ratingNode.getAttribute('aria-label')));
  const ratingMatch = ratingEvidence.match(/\b([0-5](?:[.,]\d{1,2})?)\b/);
  const reviewNode = document.querySelector(
    '[data-testid*="review"], [data-testid*="evaluation"], [aria-label*="avalia" i]'
  );
  const reviewEvidence = clean(reviewNode && (reviewNode.innerText || reviewNode.getAttribute('aria-label')));
  const reviewMatch = reviewEvidence.match(/(\d{1,7})\s*(?:avalia|opini|review)/i);
  const badgeNode = document.querySelector(
    '[data-testid*="badge"], [data-testid*="verified"], [data-testid*="ambassador"], [aria-label*="verific" i]'
  );
  const vehicleNode = document.querySelector('[data-testid*="vehicle"], [data-testid*="car"]');
  const colorNode = document.querySelector('[data-testid*="vehicle-color"], [data-testid*="car-color"]');
  const amenitiesNode = document.querySelector('[data-testid*="amenit"], [data-testid*="comfort"]');
  const preferencesNode = document.querySelector('[data-testid*="prefer"], [data-testid*="rule"]');
  const reviewAnchor = Array.from(document.querySelectorAll('a[href]')).find((anchor) => {
    const label = clean(anchor.innerText || anchor.getAttribute('aria-label'));
    return /(avalia|opini|review)/i.test(label) && uuid.test(anchor.href || '');
  });
  const reviewCandidates = Array.from(document.querySelectorAll(
    '[data-testid*="review-card"], [data-testid*="review-item"], [data-testid*="rating-card"], article[class*="review" i], li[class*="review" i]'
  ));
  const reviews = reviewCandidates.map((root) => {
    const authorNode = root.querySelector('[data-testid*="name"], strong, h3, h4');
    const dateNode = root.querySelector('time, [data-testid*="date"]');
    const textNode = root.querySelector('[data-testid*="comment"], [data-testid*="content"], p');
    const reviewRatingNode = root.querySelector('[data-testid*="rating"], [aria-label*="estrela" i], [aria-label*="nota" i]');
    const reviewRatingText = clean(reviewRatingNode && (reviewRatingNode.innerText || reviewRatingNode.getAttribute('aria-label')));
    const reviewRating = (reviewRatingText.match(/\b([0-5](?:[.,]\d{1,2})?)\b/) || [])[1] || '';
    return {
      author: clean(authorNode && authorNode.innerText).slice(0, 120),
      rating: reviewRating.replace(',', '.').slice(0, 20),
      dateLabel: clean(dateNode && (dateNode.innerText || dateNode.getAttribute('datetime'))).slice(0, 80),
      text: clean(textNode && textNode.innerText).slice(0, 600)
    };
  }).filter((item) => item.author || item.text);
  const resourceUrls = (performance && performance.getEntriesByType)
    ? performance.getEntriesByType('resource').map((entry) => entry.name || '')
    : [];
  const navigationUrls = (performance && performance.getEntriesByType)
    ? performance.getEntriesByType('navigation').map((entry) => entry.name || '')
    : [];
  const rawIdentityEvidence = [
    location.href || '',
    document.documentElement ? (document.documentElement.outerHTML || '') : '',
    ...resourceUrls,
    ...navigationUrls
  ].join('\n');
  const observedUuids = Array.from(new Set(
    (rawIdentityEvidence.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig) || [])
      .map((value) => value.toLowerCase())
  ));
  const clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
clone.querySelectorAll('input, textarea').forEach((node) => {
  node.removeAttribute('value');
  node.textContent = '';
});
const html = clone.outerHTML || '';
  return JSON.stringify({
    profileLinks: Array.from(new Set(links)),
    observedUuids: observedUuids,
    visibleName: nameCandidate,
    photoUrl: clean(photoNode && (photoNode.currentSrc || photoNode.src)),
    about: clean(aboutNode && aboutNode.innerText).slice(0, 320),
    rating: ratingMatch ? ratingMatch[1].replace(',', '.') : '',
    reviewCount: reviewMatch ? Number(reviewMatch[1]) : null,
    badge: clean(badgeNode && (badgeNode.innerText || badgeNode.getAttribute('aria-label'))).slice(0, 80),
    vehicleMakeModel: clean(vehicleNode && vehicleNode.innerText).slice(0, 120),
    vehicleColor: clean(colorNode && colorNode.innerText).slice(0, 60),
    amenities: clean(amenitiesNode && amenitiesNode.innerText).slice(0, 240),
    preferences: clean(preferencesNode && preferencesNode.innerText).slice(0, 240),
    reviewsHref: clean(reviewAnchor && reviewAnchor.href).slice(0, 1000),
    reviews: reviews.slice(0, 60),
    domHtml: html.slice(0, 350000)
  });
})();
