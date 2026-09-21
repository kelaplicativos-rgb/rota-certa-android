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
  const tripIdentity = (() => {
    try {
      const currentUrl = new URL(location.href);
      const fromQuery = clean(currentUrl.searchParams.get('id'));
      if (fromQuery) return fromQuery;
      const editMatch = currentUrl.pathname.match(/\/ride-plan\/trip-edit\/([^/?#]+)/i);
      if (editMatch && editMatch[1]) return clean(editMatch[1]);
      const offerMatch = currentUrl.pathname.match(/\/rides\/offer\/(?!edit(?:\/|$)|passenger(?:\/|$))([^/?#]+)/i);
      return clean(offerMatch && offerMatch[1]);
    } catch (_) {
      return '';
    }
  })();
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
  const options = links.find((a) => {
    const href = absolute(a.getAttribute('href') || a.href || '');
    return /\/rides\/offer\/edit\/[^/?#]+\/options\/?(?:$|[?#])/i.test(href);
  });
  const strongPassengerLinks = Array.from(document.querySelectorAll(
    'a[href*="/rides/offer/passenger/"], a[href*="/rides/offer/booking/"], a[href*="/passenger/"], a[href*="/booking/"]'
  )).filter((node) => {
    const href = absolute(node.getAttribute('href') || node.href || '');
    if (!/\/passenger\/|\/booking\//i.test(href)) return false;
    try {
      const url = new URL(href);
      return !tripIdentity || clean(url.searchParams.get('id')) === tripIdentity;
    } catch (_) {
      return false;
    }
  });
  const rosterContainers = Array.from(new Set(
    Array.from(document.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
      const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
      return marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
    }).concat(strongPassengerLinks)
  ));
  const rosterExpandControls = Array.from(document.querySelectorAll('button, a, [role="button"], [data-testid], [aria-label], [aria-controls]')).filter((node) => {
    const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '') + ' ' + (node.getAttribute('aria-controls') || '')).toLowerCase();
    const passengerMarker = marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
    const markerRequestsMore = passengerMarker && (marker.includes('more') || marker.includes('expand') || marker.includes('load'));
    const collapsedNearRoute = node.getAttribute('aria-expanded') === 'false' && /(?:→|->)/.test(clean((node.parentElement && node.parentElement.innerText) || ''));
    return markerRequestsMore || collapsedNearRoute;
  });
  const hasMore = rosterExpandControls.length > 0;
  const scrollingElement = document.scrollingElement || document.documentElement || document.body;
  const scrollY = Math.max(0, Math.round(window.scrollY || (scrollingElement && scrollingElement.scrollTop) || 0));
  const scrollHeight = Math.max(
    0,
    Math.round(
      (scrollingElement && scrollingElement.scrollHeight) ||
      (document.documentElement && document.documentElement.scrollHeight) ||
      (document.body && document.body.scrollHeight) ||
      0
    )
  );
  const viewportHeight = Math.max(
    1,
    Math.round(window.innerHeight || (document.documentElement && document.documentElement.clientHeight) || 1)
  );
  const atBottom = scrollHeight <= viewportHeight || (scrollY + viewportHeight >= scrollHeight - 8);
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
  const passengerRosterComplete = explicitEmptyRoster || (
    passengers.length > 0 &&
    strongPassengerLinks.length > 0 &&
    strongPassengerLinks.length >= passengers.length &&
    !hasMore
  );
  const rosterTerminalEvidence = !!edit || rosterContainers.length > 0 || document.readyState === 'complete';
  const placeKey = (value) => clean(value)
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  const samePlace = (left, right) => {
    const a = placeKey(left);
    const b = placeKey(right);
    return !!a && !!b && (a === b || a.startsWith(b + ' ') || b.startsWith(a + ' '));
  };
  const itineraryNodes = Array.from(document.querySelectorAll(
    '[data-testid*="itinerary-departure-station"], [data-testid*="itinerary-stop"], [data-testid*="station"], [data-testid*="itinerary-arrival-station"]'
  ));
  const semanticItineraryStops = [];
  itineraryNodes.forEach((node) => {
    const value = clean(node.innerText);
    if (value && semanticItineraryStops[semanticItineraryStops.length - 1] !== value) semanticItineraryStops.push(value);
  });

  // 0.1.606: the current BlaBlaCar offer DOM no longer exposes itinerary data-testid
  // attributes consistently. The exact administrative offer still exposes one strong,
  // trip-bound /rides/offer/map?id=<tripId> anchor per timed stop. Use the lowest common
  // ancestor of those anchors and its direct rows as the authoritative route fallback.
  const mapAnchors = Array.from(document.querySelectorAll('a[href*="/rides/offer/map"]')).filter((node) => {
    try {
      const url = new URL(node.getAttribute('href') || node.href || '', location.href);
      return /\/rides\/offer\/map\/?$/i.test(url.pathname) &&
        (!tripIdentity || clean(url.searchParams.get('id')) === tripIdentity);
    } catch (_) {
      return false;
    }
  });
  let routeRoot = null;
  if (mapAnchors.length > 0) {
    routeRoot = mapAnchors[0].parentElement;
    while (routeRoot && !mapAnchors.every((node) => routeRoot.contains(node))) {
      routeRoot = routeRoot.parentElement;
    }
  }
  const stopLabelFromRow = (row) => {
    if (!row) return '';
    const leaves = Array.from(row.querySelectorAll('span, p, div'))
      .filter((node) => node.children.length === 0)
      .map((node) => clean(node.innerText || node.textContent))
      .filter(Boolean);
    const candidate = leaves.find((value) =>
      !/^([01]?\d|2[0-3]):[0-5]\d$/.test(value) &&
      !/^(?:r\$|brl|\d+[,.]\d{2})$/i.test(value) &&
      value.length <= 120
    );
    return clean(candidate || '');
  };
  const fallbackItineraryStops = routeRoot
    ? Array.from(routeRoot.children)
        .map(stopLabelFromRow)
        .filter(Boolean)
        .filter((value, index, values) => index === 0 || value !== values[index - 1])
    : [];
  const itineraryStops = semanticItineraryStops.length >= 2
    ? semanticItineraryStops
    : fallbackItineraryStops;
  const observedOrigin = first(['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']) ||
    itineraryStops[0] || '';
  const observedDestination = first(['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']) ||
    itineraryStops[itineraryStops.length - 1] || '';
  const fallbackRouteBound = !!routeRoot && mapAnchors.length > 0 && itineraryStops.length >= 2;
  const itineraryAuthoritative = itineraryStops.length >= 2 &&
    (itineraryNodes.length >= 2 || fallbackRouteBound) &&
    samePlace(itineraryStops[0], observedOrigin) &&
    samePlace(itineraryStops[itineraryStops.length - 1], observedDestination);
  const pageText = clean(document.body && document.body.innerText);
  const viewsMatch = pageText.match(/(\d{1,9})\s+visualiza(?:ç|c)[õo]es/i);
  const views = viewsMatch ? parseInt(viewsMatch[1], 10) : null;
  let currentTripId = '';
  try {
    const currentUrl = new URL(location.href);
    currentTripId = clean(currentUrl.searchParams.get('id'));
    if (!currentTripId) {
      const currentMatch = currentUrl.pathname.match(/\/ride-plan\/trip-edit\/([^/?#]+)/i);
      currentTripId = clean(currentMatch && currentMatch[1]);
    }
    if (!currentTripId) {
      const match = currentUrl.pathname.match(/\/rides\/offer\/(?!edit(?:\/|$)|passenger(?:\/|$))([^/?#]+)/i);
      currentTripId = clean(match && match[1]);
    }
  } catch (_) {}

  const isOfficialBlaBlaHost = (hostname) => {
    const labels = clean(hostname).toLowerCase().replace(/^\.+|\.+$/g, '').split('.').filter(Boolean);
    const root = labels[0] === 'www' ? labels.slice(1) : labels;
    if (root[0] !== 'blablacar') return false;
    const suffix = root.slice(1);
    if (suffix.length === 1) return suffix[0] === 'com' || /^[a-z]{2}$/.test(suffix[0]);
    if (suffix.length === 2) return ['com', 'co'].includes(suffix[0]) && /^[a-z]{2}$/.test(suffix[1]);
    return false;
  };

  const exactPublicTripUrl = (raw) => {
    if (!currentTripId) return '';
    try {
      const url = new URL(raw || '', location.href);
      if (url.protocol !== 'https:' || !isOfficialBlaBlaHost(url.hostname)) return '';
      const path = url.pathname.replace(/\/+$/, '').toLowerCase();
      if (path !== '/trip' && !path.startsWith('/trip/')) return '';
      let id = clean(url.searchParams.get('id'));
      if (!id) {
        const match = url.pathname.match(/\/trip\/([^/?#]+)/i);
        id = clean(match && match[1]);
      }
      if (!id || id !== currentTripId) return '';
      url.searchParams.delete('search_uuid');
      url.hash = '';
      return url.href;
    } catch (_) {
      return '';
    }
  };
  const publicTripCandidates = [location.href];
  Array.from(document.querySelectorAll('a[href], [data-href]')).forEach((node) => {
    publicTripCandidates.push(node.href || node.getAttribute('href') || node.getAttribute('data-href') || '');
  });
  Array.from(document.querySelectorAll('link[rel="canonical"], meta[property="og:url"], meta[name="twitter:url"]')).forEach((node) => {
    publicTripCandidates.push(node.href || node.content || node.getAttribute('content') || '');
  });
  const publicTripHref = publicTripCandidates.map(exactPublicTripUrl).find(Boolean) || '';

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
    scrollY: scrollY,
    scrollHeight: scrollHeight,
    viewportHeight: viewportHeight,
    atBottom: atBottom,
    editHref: edit ? absolute(edit.getAttribute('href') || edit.href || '') : '',
    optionsHref: options ? absolute(options.getAttribute('href') || options.href || '') : '',
    publicTripHref: publicTripHref,
    itineraryStops: itineraryStops,
    itineraryAuthoritative: itineraryAuthoritative,
    views: Number.isFinite(views) ? views : null,
    domHtml: html.slice(0, 350000)
  });
})();
