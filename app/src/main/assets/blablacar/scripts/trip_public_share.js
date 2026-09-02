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

  const exactPublicTripUrl = (raw) => {
    if (!tripId) return '';
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
      if (!id || id !== tripId) return '';
      url.searchParams.delete('search_uuid');
      url.hash = '';
      return url.href;
    } catch (_) {
      return '';
    }
  };

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
      payloadText: ''
    };
    window[stateKey] = state;
  }

  const acceptCandidate = (raw) => {
    const exact = exactPublicTripUrl(raw);
    if (exact) {
      state.publicTripHref = exact;
      return exact;
    }
    return '';
  };

  if (!state.publicTripHref) {
    Array.from(document.querySelectorAll(
      'a[href], [data-href], [data-share-url], [data-url], link[rel="canonical"], meta[property="og:url"], meta[name="twitter:url"]'
    )).some((node) => {
      const candidates = [
        node.href,
        node.content,
        node.getAttribute && node.getAttribute('href'),
        node.getAttribute && node.getAttribute('data-href'),
        node.getAttribute && node.getAttribute('data-share-url'),
        node.getAttribute && node.getAttribute('data-url'),
        node.getAttribute && node.getAttribute('content')
      ];
      return candidates.some(acceptCandidate);
    });
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
      if (acceptCandidate(piece)) return true;
      return urlsFrom(piece).some(acceptCandidate);
    });
    return Promise.resolve();
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

  const shareControls = Array.from(document.querySelectorAll(
    'button, a, [role="button"], [data-testid], [aria-label]'
  )).filter((node) => {
    if (!visible(node)) return false;
    const marker = normalize(
      (node.innerText || node.textContent || '') + ' ' +
      ((node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
      ((node.getAttribute && node.getAttribute('data-testid')) || '')
    );
    return marker.includes('compartilhar esta carona') ||
      marker.includes('compartilhar carona') ||
      marker.includes('share this ride');
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
      return candidates.some(acceptCandidate);
    });
  }

  const canCaptureWithoutOpeningSystemShare = installShareIntercept();
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

  if (!state.publicTripHref && state.payloadText) {
    urlsFrom(state.payloadText).some(acceptCandidate);
  }

  return JSON.stringify({
    tripId: tripId,
    shareControlPresent: shareControls.length > 0,
    shareInterceptInstalled: !!state.interceptInstalled,
    shareInvoked: !!state.shareInvoked,
    clickCount: state.clicks || 0,
    publicTripHref: state.publicTripHref || ''
  });
})();