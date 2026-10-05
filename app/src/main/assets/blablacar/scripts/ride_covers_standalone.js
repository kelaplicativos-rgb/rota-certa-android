(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const first = (root, selectors) => {
    for (const selector of selectors) {
      const node = root && root.querySelector(selector);
      if (node && clean(node.innerText || node.textContent)) return clean(node.innerText || node.textContent);
    }
    return '';
  };
  const candidateHref = (root) => {
    const anchors = Array.from(root.querySelectorAll('a[href]'))
      .map((anchor) => anchor.href || '')
      .filter((href) => href && !href.includes('/rides/offer/passenger/'));
    return (
      anchors.find((href) => /\/ride-plan\/trip-edit\/[^/?#]+/i.test(href)) ||
      anchors.find((href) => /\/rides\/offer\/[^/?#]+/i.test(href) || /\/trip\/[^/?#]+/i.test(href)) ||
      anchors.find((href) => /\/rides\/offer\?[^#]*\bid=/i.test(href) || /\/trip\?[^#]*\bid=/i.test(href)) ||
      anchors.find((href) => href.includes('/rides/offer') || href.includes('/trip?') || href.includes('/trip/')) ||
      ''
    );
  };
  const looksLikeCalendarDate = (value) => {
    const text = clean(value);
    if (!text) return false;
    return /\b20\d{2}-\d{1,2}-\d{1,2}\b/.test(text) ||
      /\b\d{1,2}[\/.-]\d{1,2}(?:[\/.-]\d{2,4})?\b/.test(text) ||
      /\b(?:hoje|amanh[ãa])\b/i.test(text) ||
      /\b\d{1,2}\s*(?:de\s+)?(?:jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)\b/i.test(text);
  };
  const nearestPrecedingDateEvidence = (root) => {
    const markers = Array.from(document.querySelectorAll('[data-testid*="date"], time[datetime], h1, h2, h3'));
    for (let index = markers.length - 1; index >= 0; index--) {
      const node = markers[index];
      if (!node || node === root || (root.contains && root.contains(node))) continue;
      if (!(node.compareDocumentPosition(root) & Node.DOCUMENT_POSITION_FOLLOWING)) continue;
      const structured = clean(node.getAttribute && node.getAttribute('datetime'));
      const visible = clean(node.innerText || node.textContent);
      if (looksLikeCalendarDate(structured)) return structured;
      if (looksLikeCalendarDate(visible)) return visible;
    }
    return '';
  };
  const dateEvidence = (root) => {
    const structured = Array.from(root.querySelectorAll('time[datetime]'))
      .map((node) => clean(node.getAttribute('datetime')))
      .filter(Boolean);
    const visible = Array.from(root.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
      .map((node) => clean(node.innerText || node.textContent))
      .filter(Boolean);
    const local = structured.concat(visible);
    if (local.some(looksLikeCalendarDate)) return clean(local.join(' | ')).slice(0, 600);
    return nearestPrecedingDateEvidence(root).slice(0, 600);
  };

  const roots = Array.from(document.querySelectorAll(
    '[data-testid^="e2e-your-rides-trip-card-"], article[data-testid^="e2e-your-rides-trip-card-"], article'
  ));
  const currentCovers = roots.map((root) => {
    const href = candidateHref(root);
    if (!href) return null;
    return {
      href: href,
      departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
      arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
      origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
      destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
      price: first(root, ['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
      dateText: dateEvidence(root)
    };
  }).filter(Boolean);

  const probeKey = '__rotaCertaStandaloneRideCovers0734';
  let probe = window[probeKey];
  if (!probe || !probe.observer) {
    probe = { lastMutationAt: Date.now(), observer: null, coversByHref: {} };
    probe.observer = new MutationObserver((mutations) => {
      if (mutations.some((mutation) => mutation.type === 'childList' || mutation.type === 'characterData')) {
        probe.lastMutationAt = Date.now();
      }
    });
    probe.observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true });
    window[probeKey] = probe;
  }
  if (!probe.coversByHref) probe.coversByHref = {};
  currentCovers.forEach((cover) => {
    if (cover && cover.href) probe.coversByHref[cover.href] = cover;
  });
  const covers = Object.keys(probe.coversByHref).sort().map((href) => probe.coversByHref[href]);
  const observedTripHrefs = covers.map((cover) => cover.href);
  const observedCardCount = observedTripHrefs.length;

  const isVisible = (node) => {
    if (!node) return false;
    const style = window.getComputedStyle ? window.getComputedStyle(node) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || '1') === 0)) return false;
    return !!(node.getClientRects && node.getClientRects().length);
  };
  const loadingActive = Array.from(document.querySelectorAll(
    '[aria-busy="true"], [role="progressbar"], [data-testid*="loader" i], [data-testid*="loading" i], [class*="spinner" i]'
  )).some(isVisible);

  const bodyText = clean(document.body && document.body.innerText).slice(0, 5000);
  const emptyStructure = document.querySelector(
    '[data-testid*="empty"][data-testid*="ride"], [data-testid*="empty"][data-testid*="trip"], ' +
    '[data-testid*="no-ride"], [data-testid*="no-trip"], [aria-label*="no ride" i], [aria-label*="no trip" i]'
  );
  const emptyText = /nenhuma viagem|sem viagens|no trips|no rides|aucun trajet|keine fahrten|sin viajes|nessun viaggio/i.test(bodyText);

  const archivedSentinelPattern = /viagens? arquivadas?|archived (?:rides|trips)|trajets? archiv|fahrten archiv|viajes? archiv|viaggi? archivi/i;
  const endSentinelNode = Array.from(document.querySelectorAll('h1, h2, h3, h4, [role="heading"], summary, button, a'))
    .find((node) => isVisible(node) && archivedSentinelPattern.test(clean(node.innerText || node.textContent)));

  const documentScrollRoot = document.scrollingElement || document.documentElement || document.body;
  const firstRideRoot = roots.find((root) => !!candidateHref(root)) || null;
  const scrollableAncestor = (node) => {
    let current = node && node.parentElement;
    while (current && current !== document.body && current !== document.documentElement) {
      const style = window.getComputedStyle ? window.getComputedStyle(current) : null;
      const overflowY = clean(style && style.overflowY).toLowerCase();
      if (/^(?:auto|scroll|overlay)$/.test(overflowY) &&
          Number(current.scrollHeight || 0) > Number(current.clientHeight || 0) + 8) {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  };
  const scrollRoot = scrollableAncestor(firstRideRoot) || documentScrollRoot;
  window.__rotaCertaStandaloneRideCoversScrollRoot0734 = scrollRoot;
  const usesWindow = !scrollRoot ||
    scrollRoot === documentScrollRoot ||
    scrollRoot === document.documentElement ||
    scrollRoot === document.body;
  const scrollY = usesWindow
    ? Math.max(0, Math.round(window.scrollY || window.pageYOffset || 0))
    : Math.max(0, Math.round(scrollRoot.scrollTop || 0));
  const scrollHeight = usesWindow
    ? Math.max(0, Math.round(Math.max(document.documentElement.scrollHeight || 0, document.body.scrollHeight || 0)))
    : Math.max(0, Math.round(scrollRoot.scrollHeight || 0));
  const viewportHeight = usesWindow
    ? Math.max(0, Math.round(window.innerHeight || document.documentElement.clientHeight || 0))
    : Math.max(0, Math.round(scrollRoot.clientHeight || 0));
  const atBottom = Math.ceil(scrollY + viewportHeight) >= scrollHeight - 8;

  return JSON.stringify({
    covers: covers,
    observedTripHrefs: observedTripHrefs,
    observedCardCount: observedCardCount,
    explicitEmptyList: observedCardCount === 0 && (!!emptyStructure || emptyText),
    documentReady: document.readyState === 'complete',
    loadingActive: loadingActive,
    endSentinelVisible: !!endSentinelNode,
    lastMutationAgeMs: Math.max(0, Date.now() - Number(probe.lastMutationAt || Date.now())),
    scrollY: scrollY,
    scrollHeight: scrollHeight,
    viewportHeight: viewportHeight,
    atBottom: atBottom
  });
})();