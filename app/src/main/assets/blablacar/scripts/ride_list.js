(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const first = (root, selectors) => {
    for (const selector of selectors) {
      const node = root && root.querySelector(selector);
      if (node && clean(node.innerText)) return clean(node.innerText);
    }
    return '';
  };
  const candidateHref = (root) => {
    const anchors = Array.from(root.querySelectorAll('a[href]'))
      .map((anchor) => ({ anchor, href: anchor.href || '' }))
      .filter((item) => item.href && !item.href.includes('/rides/offer/passenger/'));
    return (
      anchors.find((item) => /\/rides\/offer\/[^/?#]+/i.test(item.href) || /\/trip\/[^/?#]+/i.test(item.href)) ||
      anchors.find((item) => /\/rides\/offer\?[^#]*\bid=/i.test(item.href) || /\/trip\?[^#]*\bid=/i.test(item.href)) ||
      anchors.find((item) => item.href.includes('/rides/offer') || item.href.includes('/trip?') || item.href.includes('/trip/')) ||
      null
    );
  };
  const normalizePassengerName = (value) => clean(value)
    .replace(/\s*[•|].*$/, '')
    .replace(/\s*\(\d+\)\s*$/, '');
  const extractPassengers = (root) => {
    const passengerMap = new Map();
    const scoped = Array.from(root.querySelectorAll('a[href*="/rides/offer/passenger/"], [data-testid*="passenger"], [data-testid*="booking"]'));
    const hintedRoot = /passageir|reserva/i.test(clean(root.innerText));
    if (hintedRoot) {
      Array.from(root.querySelectorAll('img[alt]')).forEach((image) => {
        const scope = image.closest('a[href*="/rides/offer/passenger/"], [data-testid*="passenger"], [data-testid*="booking"]');
        if (scope) scoped.push(scope);
      });
    }
    scoped.forEach((node, index) => {
      const link = (node.matches && node.matches('a[href*="/rides/offer/passenger/"]'))
        ? node
        : (node.querySelector && node.querySelector('a[href*="/rides/offer/passenger/"]'));
      const href = link ? (link.href || '') : '';
      const container = node.closest && (node.closest('li, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]') || node);
      const raw = clean((container && container.innerText) || node.innerText || '');
      const lines = raw.split(/\n+/).map(clean).filter(Boolean);
      const explicitName = container && container.querySelector
        ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
        : null;
      const alt = explicitName && explicitName.getAttribute ? clean(explicitName.getAttribute('alt')) : '';
      let name = normalizePassengerName(alt || clean(explicitName && explicitName.innerText) || lines[0] || '');
      if (!name || /^(foto|avatar|perfil|blablacar|passageiro|passageira)$/i.test(name)) return;
      const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
      const suffix = suffixSource.match(/\((\d+)\)\s*$/);
      const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
      name = normalizePassengerName(name);
      const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
      const routeParts = route.split(/→|->/).map(clean);
      const tel = container && container.querySelector ? container.querySelector('a[href^="tel:"]') : null;
      const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
      if (!passengerMap.has(key)) {
        passengerMap.set(key, {
          name: name,
          seats: seats,
          boarding: routeParts.length >= 2 ? routeParts[0] : null,
          dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
          phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
          booking_href: href || null
        });
      }
    });
    const passengers = Array.from(passengerMap.values()).filter((item) => item.name);
    const rosterContainers = Array.from(root.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
      const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
      return marker.includes('passenger') || marker.includes('passageir') || marker.includes('booking') || marker.includes('reserva');
    });
    const hasMore = Array.from(root.querySelectorAll('button, a, [role="button"]')).some((node) => /mostrar mais|ver mais|mais passageir|mais reserva/i.test(clean(node.innerText)));
    return {
      passengers: passengers,
      passengerRosterComplete: rosterContainers.length > 0 && !hasMore
    };
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
      .map((node) => clean(node.innerText))
      .filter(Boolean);
    const localEvidence = structured.concat(visible);
    if (localEvidence.some(looksLikeCalendarDate)) {
      return clean(localEvidence.join(' | ')).slice(0, 1200);
    }
    const preceding = nearestPrecedingDateEvidence(root);
    return clean(localEvidence.concat(preceding ? [preceding] : []).join(' | ')).slice(0, 1200);
  };
  const roots = Array.from(document.querySelectorAll('[data-testid^="e2e-your-rides-trip-card-"], article[data-testid^="e2e-your-rides-trip-card-"], article'));
  const fromRoots = roots.map((root) => {
    const selected = candidateHref(root);
    if (!selected) return null;
    const roster = extractPassengers(root);
    return {
      href: selected.href || '',
      text: clean(root.innerText).slice(0, 3200),
      departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
      arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
      origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
      destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
      price: first(root, ['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
      dateText: dateEvidence(root),
      passengers: roster.passengers,
      passengerRosterComplete: roster.passengerRosterComplete
    };
  }).filter(Boolean);
  const fallback = fromRoots.length ? [] : Array.from(document.querySelectorAll('a[href*="/rides/offer"], a[href*="/trip?"], a[href*="/trip/"]'))
    .filter((anchor) => !(anchor.href || '').includes('/rides/offer/passenger/'))
    .map((anchor) => {
      const root = anchor.closest('article, li, section, div') || anchor.parentElement || document.body;
      const roster = extractPassengers(root);
      return {
        href: anchor.href || '',
        text: clean(root.innerText).slice(0, 3200),
        departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
        arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
        origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
        destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
        price: first(root, ['[data-testid*="price"]']),
        dateText: dateEvidence(root),
        passengers: roster.passengers,
        passengerRosterComplete: roster.passengerRosterComplete
      };
    });
  const bodyText = clean(document.body && document.body.innerText).slice(0, 16000);
  const emptyStructure = document.querySelector(
    '[data-testid*="empty"][data-testid*="ride"], [data-testid*="empty"][data-testid*="trip"], ' +
    '[data-testid*="no-ride"], [data-testid*="no-trip"], [aria-label*="no ride" i], [aria-label*="no trip" i]'
  );
  const emptyText = /nenhuma viagem|sem viagens|no trips|no rides|aucun trajet|keine fahrten|sin viajes|nessun viaggio/i.test(bodyText);

  // Persistent, page-local stabilization evidence. It observes DOM materialization only;
  // it does not click, mutate BlaBlaCar data, or create a second collection path.
  const probeKey = '__rotaCertaRidesSnapshotProbe0526';
  let probe = window[probeKey];
  if (!probe || !probe.observer) {
    probe = { lastMutationAt: Date.now(), observer: null, observedRideHrefs: {} };
    probe.observer = new MutationObserver((mutations) => {
      if (mutations.some((mutation) => mutation.type === 'childList' || mutation.type === 'characterData')) {
        probe.lastMutationAt = Date.now();
      }
    });
    probe.observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true });
    window[probeKey] = probe;
  }
  if (!probe.observedRideHrefs) probe.observedRideHrefs = {};
  const currentCandidates = fromRoots.concat(fallback);
  currentCandidates.forEach((candidate) => {
    const href = clean(candidate && candidate.href);
    if (href) probe.observedRideHrefs[href] = true;
  });
  const observedCardCount = Object.keys(probe.observedRideHrefs).length;
  const isVisible = (node) => {
    if (!node) return false;
    const style = window.getComputedStyle ? window.getComputedStyle(node) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || '1') === 0)) return false;
    return !!(node.getClientRects && node.getClientRects().length);
  };
  const loadingActive = Array.from(document.querySelectorAll(
    '[aria-busy="true"], [role="progressbar"], [data-testid*="loader" i], [data-testid*="loading" i], [class*="spinner" i]'
  )).some(isVisible);
  const archivedSentinelPattern = /viagens? arquivadas?|archived (?:rides|trips)|trajets? archiv|fahrten archiv|viajes? archiv|viaggi? archivi/i;
  const endSentinelNode = Array.from(document.querySelectorAll(
    'h1, h2, h3, h4, [role="heading"], summary, button, a'
  )).find((node) => isVisible(node) && archivedSentinelPattern.test(clean(node.innerText || node.textContent)));
  const endSentinelText = clean(endSentinelNode && (endSentinelNode.innerText || endSentinelNode.textContent)).slice(0, 160);

  // Keep the historical small diagnostic DOM unchanged for the existing collector.
  const diagnosticClone = document.documentElement.cloneNode(true);
  diagnosticClone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
  diagnosticClone.querySelectorAll('input, textarea').forEach((node) => {
    node.removeAttribute('value');
    node.textContent = '';
  });
  const diagnosticHtml = diagnosticClone.outerHTML || '';

  // Private forensic HTML keeps rendered style/link structure but removes executable scripts
  // and form values. It is never published by the collector or Agenda pipeline.
  const snapshotClone = document.documentElement.cloneNode(true);
  snapshotClone.querySelectorAll('script, noscript').forEach((node) => node.remove());
  snapshotClone.querySelectorAll('meta[http-equiv="set-cookie" i]').forEach((node) => node.remove());
  snapshotClone.querySelectorAll('input').forEach((node) => {
    node.removeAttribute('value');
    node.removeAttribute('checked');
  });
  snapshotClone.querySelectorAll('textarea').forEach((node) => {
    node.removeAttribute('value');
    node.textContent = '';
  });
  const sensitiveAttributeName = /(?:token|authorization|cookie|session|password|passwd|secret|credential|csrf)/i;
  const sensitiveQuery = /([?&])(?:access_token|refresh_token|id_token|auth_token|authorization|session_token|session_id|sessionid|csrf_token)=[^&#"'\s]*/ig;
  snapshotClone.querySelectorAll('*').forEach((node) => {
    Array.from(node.attributes || []).forEach((attribute) => {
      const name = String(attribute.name || '');
      if (/^on/i.test(name) || sensitiveAttributeName.test(name)) {
        node.removeAttribute(name);
        return;
      }
      if (/^(?:href|src|action|formaction)$/i.test(name)) {
        const scrubbed = String(attribute.value || '')
          .replace(sensitiveQuery, '$1')
          .replace(/[?&]+$/, '');
        if (scrubbed !== attribute.value) node.setAttribute(name, scrubbed);
      }
    });
  });
  if (document.createTreeWalker && typeof NodeFilter !== 'undefined') {
    const comments = [];
    const walker = document.createTreeWalker(snapshotClone, NodeFilter.SHOW_COMMENT);
    while (walker.nextNode()) comments.push(walker.currentNode);
    comments.forEach((node) => node.parentNode && node.parentNode.removeChild(node));
  }
  const fullSnapshotHtml = '<!doctype html>\n' + (snapshotClone.outerHTML || '');
  const maxSnapshotChars = 1500000;
  const snapshotTruncated = fullSnapshotHtml.length > maxSnapshotChars;
  const snapshotHtml = fullSnapshotHtml.slice(0, maxSnapshotChars);

  return JSON.stringify({
    candidates: currentCandidates,
    observedCardCount: observedCardCount,
    snapshotContainsAllObservedCards: currentCandidates.length >= observedCardCount,
    bodyText: bodyText,
    explicitEmptyList: !!emptyStructure || emptyText,
    documentReady: document.readyState === 'complete',
    loadingActive: loadingActive,
    endSentinelVisible: !!endSentinelNode,
    endSentinelText: endSentinelText,
    lastMutationAgeMs: Math.max(0, Date.now() - Number(probe.lastMutationAt || Date.now())),
    scrollY: Math.max(0, Math.round(window.scrollY || window.pageYOffset || 0)),
    scrollHeight: Math.max(0, Math.round(document.documentElement.scrollHeight || document.body.scrollHeight || 0)),
    viewportHeight: Math.max(0, Math.round(window.innerHeight || document.documentElement.clientHeight || 0)),
    atBottom: Math.ceil((window.scrollY || window.pageYOffset || 0) + (window.innerHeight || document.documentElement.clientHeight || 0)) >= Math.max(document.documentElement.scrollHeight || 0, document.body.scrollHeight || 0) - 8,
    snapshotHtml: snapshotHtml,
    snapshotHtmlLength: fullSnapshotHtml.length,
    snapshotTruncated: snapshotTruncated,
    domHtml: diagnosticHtml.slice(0, 350000)
  });
})();
