(function() {
  const clean = (value) => String(value || '').replace(/\s+/g, ' ').trim();
  const normalize = (value) => clean(value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
  let tripId = '';
  try {
    const current = new URL(location.href);
    tripId = clean(current.searchParams.get('id'));
    if (!tripId) {
      const currentMatch = current.pathname.match(/\/ride-plan\/trip-edit\/([^/?#]+)/i);
      tripId = clean(currentMatch && currentMatch[1]);
    }
    if (!tripId) {
      const match = current.pathname.match(/\/rides\/offer\/(?!edit(?:\/|$)|passenger(?:\/|$))([^/?#]+)/i);
      tripId = clean(match && match[1]);
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

  const publicTripUrl = (raw, requireAdministrativeId) => {
    if (!tripId) return '';
    try {
      const url = new URL(raw || '', location.href);
      if (!['http:', 'https:'].includes(url.protocol) || !isOfficialBlaBlaHost(url.hostname)) return '';
      if (url.username || url.password || (url.port && !['80', '443'].includes(url.port))) return '';
      const path = url.pathname.replace(/\/+$/, '').toLowerCase();
      if (path !== '/trip' && !path.startsWith('/trip/')) return '';
      if (['requested_seats', 'search_origin', 'search_uuid'].some((key) => url.searchParams.has(key))) return '';
      const sourceParam = clean(url.searchParams.get('source')).toUpperCase();
      if (sourceParam && sourceParam !== 'CARPOOLING') return '';
      let id = clean(url.searchParams.get('id'));
      if (!id) {
        const match = url.pathname.match(/\/trip\/([^/?#]+)/i);
        id = clean(match && match[1]);
      }
      if (!/^[A-Za-z0-9_-]{6,}$/.test(id)) return '';
      if (requireAdministrativeId && id !== tripId) return '';
      url.protocol = 'https:';
      if (url.port === '80' || url.port === '443') url.port = '';
      url.hash = '';
      return url.href;
    } catch (_) {
      return '';
    }
  };

  const exactPublicTripUrl = (raw) => publicTripUrl(raw, true);
  const authoritativeSharedPublicTripUrl = (raw) => publicTripUrl(raw, false);

  const urlsFrom = (value) => {
    const text = String(value || '');
    const matches = text.match(/https?:\/\/[^\s<>"']+/gi) || [];
    return matches.map((item) => item.replace(/[)\],.;!?]+$/, ''));
  };

  const stateKey = '__rotaCertaTripPublicShareCapture';
  let state = window[stateKey];
  if (!state || state.tripId !== tripId) {
    state = {
      tripId: tripId,
      publicTripHref: '',
      interceptInstalled: false,
      shareInvoked: false,
      clicks: 0,
      payloadText: '',
      copyClicks: 0,
      clipboardInterceptInstalled: false
    };
    window[stateKey] = state;
  }

  const acceptCandidate = (raw, authoritativeSharePayload) => {
    const resolved = authoritativeSharePayload
      ? authoritativeSharedPublicTripUrl(raw)
      : exactPublicTripUrl(raw);
    if (resolved) {
      state.publicTripHref = resolved;
      return resolved;
    }
    return '';
  };

  // 0.1.569/0571: generic page links are not public-share authority.
  // A public token may differ from the administrative trip id, so capture the
  // actual share payload instead of accepting a search/navigation URL.
  if (state.publicTripHref && !authoritativeSharedPublicTripUrl(state.publicTripHref)) {
    state.publicTripHref = '';
  }

  const capturePayload = (payload) => {
    state.shareInvoked = true;
    const pieces = [];
    if (payload && typeof payload === 'object') {
      pieces.push(payload.url || '', payload.text || '', payload.title || '');
    } else {
      pieces.push(payload || '');
    }
    state.payloadText = pieces.map(String).join(' ').slice(0, 4000);
    pieces.some((piece) => {
      if (acceptCandidate(piece, true)) return true;
      return urlsFrom(piece).some((candidate) => acceptCandidate(candidate, true));
    });
    return Promise.resolve();
  };

  const installClipboardIntercept = () => {
    if (state.clipboardInterceptInstalled) return true;
    const captureClipboard = (value) => {
      const raw = String(value || '');
      state.payloadText = (state.payloadText + ' ' + raw).slice(0, 4000);
      if (!acceptCandidate(raw, true)) {
        urlsFrom(raw).some((candidate) => acceptCandidate(candidate, true));
      }
      return Promise.resolve();
    };
    try {
      if (navigator.clipboard) {
        Object.defineProperty(navigator.clipboard, 'writeText', {
          configurable: true,
          writable: true,
          value: captureClipboard
        });
        state.clipboardInterceptInstalled = navigator.clipboard.writeText === captureClipboard;
      }
    } catch (_) {}
    if (!state.clipboardInterceptInstalled) {
      try {
        const clipboard = navigator.clipboard;
        const proto = clipboard && Object.getPrototypeOf(clipboard);
        if (proto) {
          Object.defineProperty(proto, 'writeText', {
            configurable: true,
            writable: true,
            value: captureClipboard
          });
          state.clipboardInterceptInstalled = clipboard.writeText === captureClipboard;
        }
      } catch (_) {}
    }
    return state.clipboardInterceptInstalled;
  };

  const installShareIntercept = () => {
    if (state.interceptInstalled) return true;
    try {
      Object.defineProperty(navigator, 'share', {
        configurable: true,
        writable: true,
        value: capturePayload
      });
      state.interceptInstalled = navigator.share === capturePayload;
    } catch (_) {}
    if (!state.interceptInstalled) {
      try {
        const proto = Object.getPrototypeOf(navigator);
        Object.defineProperty(proto, 'share', {
          configurable: true,
          writable: true,
          value: capturePayload
        });
        state.interceptInstalled = navigator.share === capturePayload;
      } catch (_) {}
    }
    if (state.interceptInstalled) {
      try {
        Object.defineProperty(navigator, 'canShare', {
          configurable: true,
          writable: true,
          value: function() { return true; }
        });
      } catch (_) {}
    }
    return state.interceptInstalled;
  };

  const visible = (node) => {
    if (!node || !node.isConnected) return false;
    const style = window.getComputedStyle ? window.getComputedStyle(node) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
    return !node.getClientRects || node.getClientRects().length > 0;
  };

  const markerFor = (node) => normalize(
    (node.innerText || node.textContent || '') + ' ' +
    ((node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
    ((node.getAttribute && node.getAttribute('data-testid')) || '') + ' ' +
    ((node.getAttribute && node.getAttribute('title')) || '') + ' ' +
    ((node.getAttribute && node.getAttribute('name')) || '')
  );

  const shareControls = Array.from(document.querySelectorAll(
    'button, a, [role="button"], [data-testid], [aria-label], [title]'
  )).filter((node) => {
    if (!visible(node)) return false;
    const marker = markerFor(node);
    if (marker.includes('perfil') || marker.includes('profile')) return false;
    return marker.includes('compartilhar esta carona') ||
      marker.includes('compartilhar carona') ||
      marker.includes('share this ride') ||
      marker === 'compartilhar' ||
      marker === 'share' ||
      marker.includes('share-ride') ||
      marker.includes('share_ride') ||
      marker.includes('ride-share') ||
      marker.includes('trip-share') ||
      marker.includes('share-trip') ||
      marker.includes('e2e-share');
  });

  const shareSurfaces = Array.from(document.querySelectorAll(
    '[role="dialog"], [aria-modal="true"], [data-testid*="share" i], [class*="share" i]'
  )).filter(visible);

  const copyControls = Array.from(document.querySelectorAll(
    'button, a, [role="button"], [data-testid], [aria-label], [title]'
  )).filter((node) => {
    if (!visible(node)) return false;
    const marker = markerFor(node);
    return marker.includes('copiar link') ||
      marker.includes('copie o link') ||
      marker.includes('copy link') ||
      marker.includes('copy-link') ||
      marker.includes('copy_link') ||
      marker.includes('e2e-copy');
  });

  if (!state.publicTripHref) {
    shareControls.some((node) => {
      const candidates = [
        node.href,
        node.getAttribute && node.getAttribute('href'),
        node.getAttribute && node.getAttribute('data-href'),
        node.getAttribute && node.getAttribute('data-share-url'),
        node.getAttribute && node.getAttribute('data-url')
      ];
      return candidates.some((candidate) => acceptCandidate(candidate, true));
    });
  }

  if (!state.publicTripHref && state.clicks > 0) {
    shareSurfaces.some((surface) => {
      const nodes = [surface].concat(Array.from(surface.querySelectorAll(
        'a, button, input, textarea, [data-href], [data-url], [data-share-url], [value]'
      )));
      return nodes.some((node) => {
        const candidates = [
          node.href,
          node.value,
          node.getAttribute && node.getAttribute('href'),
          node.getAttribute && node.getAttribute('value'),
          node.getAttribute && node.getAttribute('data-href'),
          node.getAttribute && node.getAttribute('data-share-url'),
          node.getAttribute && node.getAttribute('data-url')
        ];
        if (candidates.some((candidate) => acceptCandidate(candidate, true))) return true;
        return urlsFrom(node.innerText || node.textContent || '').some((candidate) =>
          acceptCandidate(candidate, true)
        );
      });
    });
  }

  const canCaptureWithoutOpeningSystemShare =
    installShareIntercept() || installClipboardIntercept();
  if (
    !state.publicTripHref &&
    canCaptureWithoutOpeningSystemShare &&
    shareControls.length > 0 &&
    state.clicks < 3
  ) {
    state.clicks += 1;
    try {
      shareControls[0].click();
    } catch (_) {}
  }

  if (
    !state.publicTripHref &&
    state.clicks > 0 &&
    copyControls.length > 0 &&
    state.copyClicks < 3
  ) {
    state.copyClicks += 1;
    try {
      copyControls[0].click();
    } catch (_) {}
  }

  if (!state.publicTripHref && state.payloadText) {
    urlsFrom(state.payloadText).some((candidate) => acceptCandidate(candidate, true));
  }

  return JSON.stringify({
    tripId: tripId,
    shareControlPresent: shareControls.length > 0,
    shareInterceptInstalled: !!state.interceptInstalled || !!state.clipboardInterceptInstalled,
    shareInvoked: !!state.shareInvoked,
    clickCount: state.clicks || 0,
    publicTripHref: state.publicTripHref || ''
  });
})();