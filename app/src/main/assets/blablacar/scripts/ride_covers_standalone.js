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
  // A date visible in the card itself has priority over a list-level heading.
  // Never infer a date by position or by the date of an adjacent journey.
  const uniqueCalendarToken = (text) => {
    const normalized = clean(text);
    const matches = normalized.match(/\b(?:20\d{2}-\d{1,2}-\d{1,2}|\d{1,2}[-.\x2f]\d{1,2}(?:[\\/.-]\d{2,4})?|\d{1,2}\s*(?:de\s+)?(?:jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)(?:\s+de\s+20\d{2})?)\b/gi) || [];
    const unique = Array.from(new Set(matches.map((value) => clean(value).toLowerCase())));
    return unique.length === 1 ? matches[0] : '';
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
    const labeled = [
      root.getAttribute && root.getAttribute('aria-label'),
      root.getAttribute && root.getAttribute('data-date'),
      root.getAttribute && root.getAttribute('data-datetime'),
      root.getAttribute && root.getAttribute('datetime'),
    ].map(uniqueCalendarToken).filter(Boolean);
    if (labeled.length === 1) return labeled[0].slice(0, 600);

    const ownDate = uniqueCalendarToken(root.innerText || root.textContent);
    if (ownDate) return ownDate.slice(0, 600);

    // A parent may be a date-group wrapper. Use it only when it contains
    // exactly this ride card; a shared list would be ambiguous.
    let ancestor = root.parentElement;
    for (let depth = 0; ancestor && depth < 3; depth++, ancestor = ancestor.parentElement) {
      const containedCards = Array.from(ancestor.querySelectorAll(
        '[data-testid^="e2e-your-rides-trip-card-"]'
      )).filter((node) => node !== root && node !== ancestor);
      if (containedCards.length > 0) break;
      const parentDate = uniqueCalendarToken(ancestor.innerText || ancestor.textContent);
      if (parentDate) return parentDate.slice(0, 600);
    }
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
  if (!probe) {
    probe = {
      coversByHref: {},
      coverInventoryFingerprint: '',
      lastCoverInventoryChangeAt: Date.now()
    };
    window[probeKey] = probe;
  }
  if (!probe.coversByHref) probe.coversByHref = {};
  currentCovers.forEach((cover) => {
    if (!cover || !cover.href) return;
    const prior = probe.coversByHref[cover.href];
    // Virtualized lists may drop the visible date after scrolling. Preserve
    // only proven date text for the *same* administrative href in this WebView.
    if (prior && !looksLikeCalendarDate(cover.dateText) &&
        looksLikeCalendarDate(prior.dateText)) {
      cover.dateText = prior.dateText;
    }
    probe.coversByHref[cover.href] = cover;
  });
  let covers = Object.keys(probe.coversByHref).sort().map((href) => probe.coversByHref[href]);
  let observedTripHrefs = covers.map((cover) => cover.href);
  let observedCardCount = observedTripHrefs.length;

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

  if (endSentinelNode) {
    roots.forEach((root) => {
      const isAfterArchivedSentinel =
        !!(endSentinelNode.compareDocumentPosition(root) & Node.DOCUMENT_POSITION_FOLLOWING);
      if (!isAfterArchivedSentinel) return;
      const href = candidateHref(root);
      if (href && probe.coversByHref[href]) delete probe.coversByHref[href];
    });
    covers = Object.keys(probe.coversByHref).sort().map((href) => probe.coversByHref[href]);
    observedTripHrefs = covers.map((cover) => cover.href);
    observedCardCount = observedTripHrefs.length;
  }

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

  const coverInventoryFingerprint = covers.map((cover) => [
    cover.href,
    cover.dateText,
    cover.departureTime,
    cover.arrivalTime,
    cover.origin,
    cover.destination,
    cover.price
  ].join('|')).join('\n');
  if (probe.coverInventoryFingerprint !== coverInventoryFingerprint) {
    probe.coverInventoryFingerprint = coverInventoryFingerprint;
    probe.lastCoverInventoryChangeAt = Date.now();
  }

  return JSON.stringify({
    covers: covers,
    observedTripHrefs: observedTripHrefs,
    observedCardCount: observedCardCount,
    explicitEmptyList: observedCardCount === 0 && (!!emptyStructure || emptyText),
    documentReady: document.readyState === 'complete',
    loadingActive: loadingActive,
    endSentinelVisible: !!endSentinelNode,
    lastMutationAgeMs: Math.max(0, Date.now() - Number(probe.lastCoverInventoryChangeAt || Date.now())),
    scrollY: scrollY,
    scrollHeight: scrollHeight,
    viewportHeight: viewportHeight,
    atBottom: atBottom
  });
})();