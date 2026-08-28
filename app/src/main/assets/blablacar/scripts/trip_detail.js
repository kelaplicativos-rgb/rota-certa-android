(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const first = (selectors) => {
    for (const selector of selectors) {
      const node = document.querySelector(selector);
      if (node && clean(node.innerText)) return clean(node.innerText);
    }
    return '';
  };
  const driverNode = document.querySelector('[data-testid="e2e-tripcard-driver-name"], [data-testid*="driver-name"], [data-testid*="driver"]');
  const driverLinks = [];
  if (driverNode) {
    const direct = driverNode.closest('a[href]');
    if (direct) driverLinks.push(direct.href);
    const root = driverNode.closest('section, article, li, div');
    if (root) Array.from(root.querySelectorAll('a[href]')).forEach((a) => driverLinks.push(a.href));
  }
  const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
  const scopedDriverLinks = Array.from(new Set(driverLinks.filter((href) => uuid.test(href))));
  const allProfileLinks = Array.from(new Set(Array.from(document.querySelectorAll('a[href]'))
    .map((a) => a.href || '')
    .filter((href) => uuid.test(href) && /(profile|user|member)/i.test(href))));
  const structuredDates = Array.from(document.querySelectorAll('time[datetime]'))
    .map((node) => clean(node.getAttribute('datetime')))
    .filter(Boolean);
  const visibleDates = Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
    .map((node) => clean(node.innerText))
    .filter(Boolean);
  const dateText = clean(structuredDates.concat(visibleDates).join(' | ')).slice(0, 1600);
  const linesOf = (node) => ((node && node.innerText) || '').split(/\n+/).map(clean).filter(Boolean);
  const absolute = (href) => { try { return new URL(href || '', location.href).href; } catch (_) { return href || ''; } };
  const rows = [];
  const seenPassengers = new Set();
  const passengerTargets = [];
  const candidateNodes = Array.from(document.querySelectorAll(
    'a[href*="passenger"], a[href*="booking"], [data-testid*="passenger"], [data-testid*="booking"], [role="link"]'
  ));
  Array.from(document.querySelectorAll('a[href], [role="link"], button')).forEach((node) => {
    const text = clean(node.innerText);
    if ((text.includes('→') || text.includes('->')) && !candidateNodes.includes(node)) candidateNodes.push(node);
  });
  candidateNodes.forEach((node, index) => {
    const anchor = (node.matches && node.matches('a[href]')) ? node : (node.querySelector && node.querySelector('a[href]'));
    const href = absolute((anchor && anchor.getAttribute('href')) || (node.getAttribute && node.getAttribute('data-href')) || '');
    const container = (node.closest && node.closest('li, article, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]')) || node;
    const lines = linesOf(container);
    const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
    if (!route) return;
    const explicit = container && container.querySelector
      ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
      : null;
    const marker = clean(
      ((container && container.getAttribute && container.getAttribute('data-testid')) || '') + ' ' +
      ((container && container.getAttribute && container.getAttribute('aria-label')) || '')
    ).toLowerCase();
    const passengerMarked = /passenger|booking|reservation/i.test(marker) || /passenger|booking/i.test(href);
    if (!explicit && !passengerMarked) return;
    const alt = explicit && explicit.getAttribute ? clean(explicit.getAttribute('alt')) : '';
    let name = clean(alt || (explicit && explicit.innerText) || lines[0] || '').replace(/\s*\(\d+\)\s*$/, '');
    if (!name) return;
    const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
    const suffix = suffixSource.match(/\((\d+)\)\s*$/);
    const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
    const routeParts = route.split(/→|->/).map(clean);
    const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
    if (seenPassengers.has(key)) return;
    seenPassengers.add(key);
    const rowIndex = rows.length;
    const realPassengerHref = /\/passenger\/|\/booking\//i.test(href) ? href : '';
    passengerTargets.push(realPassengerHref || 'rotacerta-card:' + rowIndex);
    const tel = container && container.querySelector ? container.querySelector('a[href^="tel:"]') : null;
    rows.push({
      name: name,
      seats: seats,
      boarding: routeParts.length >= 2 ? routeParts[0] : null,
      dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
      phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
      booking_href: realPassengerHref || null
    });
  });
  Array.from(document.querySelectorAll('a[href]'))
    .map((a) => absolute(a.getAttribute('href') || ''))
    .filter((href) => /\/passenger\/|\/booking\//i.test(href))
    .forEach((href) => passengerTargets.push(href));
  const passengers = rows;
  const links = Array.from(document.querySelectorAll('a[href]'));
  const edit = links.find((a) => {
    const href = absolute(a.getAttribute('href') || a.href || '');
    return /\/rides\/offer\/edit\/[^/?#]+\/?(?:$|[?#])/i.test(href) && !/\/options\/?(?:$|[?#])/i.test(href);
  });
  const rosterContainers = Array.from(document.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
    const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
    return marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
  });
  const rosterExpandControls = Array.from(document.querySelectorAll('button, a, [role="button"], [data-testid], [aria-label], [aria-controls]')).filter((node) => {
    const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '') + ' ' + (node.getAttribute('aria-controls') || '')).toLowerCase();
    const passengerMarker = marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
    const markerRequestsMore = passengerMarker && (marker.includes('more') || marker.includes('expand') || marker.includes('load'));
    const collapsedNearRoute = node.getAttribute('aria-expanded') === 'false' && /(?:→|->)/.test(clean((node.parentElement && node.parentElement.innerText) || ''));
    return markerRequestsMore || collapsedNearRoute;
  });
  const hasMore = rosterExpandControls.length > 0;
  const isVisible = (node) => {
    if (!node || !node.isConnected) return false;
    const style = window.getComputedStyle ? window.getComputedStyle(node) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
    return !node.getClientRects || node.getClientRects().length > 0;
  };
  const emptyRosterText = /^(?:nenhum(?:a)? passageir[oa].{0,50}(?:carona|viagem|reserva)|sem passageir[oa]s?(?: nesta carona)?|no passengers?(?: on this ride)?|aucun passager|keine mitfahrer|sin pasajeros|nessun passeggero)$/i;
  const explicitEmptyRoster = passengers.length === 0 && Array.from(document.querySelectorAll(
    'p, span, h1, h2, h3, [role="status"], [data-testid*="passenger"], [data-testid*="booking"], [data-testid*="reservation"]'
  )).some((node) => {
    const text = clean(node.innerText || node.textContent);
    return isVisible(node) && text.length > 0 && text.length <= 160 && emptyRosterText.test(text);
  });
  const passengerRosterComplete = explicitEmptyRoster || (passengers.length > 0 && rosterContainers.length > 0 && !hasMore);
  const rosterTerminalEvidence = !!edit || rosterContainers.length > 0 || document.readyState === 'complete';
  const itineraryStops = [];
  [
    '[data-testid*="itinerary-departure-station"]',
    '[data-testid*="itinerary-arrival-station"]',
    '[data-testid*="itinerary-stop"]',
    '[data-testid*="station"]'
  ].forEach((selector) => {
    Array.from(document.querySelectorAll(selector)).forEach((node) => {
      const value = clean(node.innerText);
      if (value && !itineraryStops.includes(value)) itineraryStops.push(value);
    });
  });
  const pageText = clean(document.body && document.body.innerText);
  const viewsMatch = pageText.match(/(\d{1,9})\s+visualiza(?:ç|c)[õo]es/i);
  const views = viewsMatch ? parseInt(viewsMatch[1], 10) : null;
  let currentTripId = '';
  try {
    const currentUrl = new URL(location.href);
    currentTripId = clean(currentUrl.searchParams.get('id'));
    if (!currentTripId) {
      const match = currentUrl.pathname.match(/\/rides\/offer\/(?!edit(?:\/|$)|passenger(?:\/|$))([^/?#]+)/i);
      currentTripId = clean(match && match[1]);
    }
  } catch (_) {}
  const networkSource = typeof window.__rotaCertaNetworkTripSource === 'function'
    ? window.__rotaCertaNetworkTripSource(currentTripId)
    : null;
  const clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
clone.querySelectorAll('input, textarea').forEach((node) => {
  node.removeAttribute('value');
  node.textContent = '';
});
const html = clone.outerHTML || '';
  return JSON.stringify({
    detail: {
      url: location.href,
      bodyText: clean(document.body && document.body.innerText).slice(0, 16000),
      dateText: dateText,
      departureTime: first(['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
      arrivalTime: first(['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
      origin: first(['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
      destination: first(['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
      price: first(['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
      driverName: clean(driverNode && driverNode.innerText),
      profileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,
      passengers: passengers,
      passengerRosterComplete: passengerRosterComplete
    },
    networkSource: networkSource,
    driverProfileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,
    passengerHrefs: Array.from(new Set(passengerTargets)),
    explicitEmptyRoster: explicitEmptyRoster,
    rosterHasMore: hasMore,
    rosterTerminalEvidence: rosterTerminalEvidence,
    editHref: edit ? absolute(edit.getAttribute('href') || edit.href || '') : '',
    itineraryStops: itineraryStops,
    views: Number.isFinite(views) ? views : null,
    domHtml: html.slice(0, 350000)
  });
})();
