(function() {
  const clean = (value) => String(value || '').replace(/\s+/g, ' ').trim();
  const digitsOnly = (value) => String(value || '').replace(/\D/g, '');
  const normalizePhone = (value) => {
    const raw = clean(value).replace(/^tel:/i, '').split('?')[0];
    let digits = digitsOnly(raw);
    if (digits.startsWith('00') && digits.length > 12) digits = digits.slice(2);
    if (digits.length < 10 || digits.length > 15) return '';
    if (digits.startsWith('55') && digits.length >= 12 && digits.length <= 13) return '+' + digits;
    if (digits.length === 10 || digits.length === 11) return '+55' + digits;
    return raw.trim().startsWith('+') ? '+' + digits : digits;
  };
  const numberOrNull = (value) => {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  };
  const validLatitude = (value) => value !== null && value >= -90 && value <= 90;
  const validLongitude = (value) => value !== null && value >= -180 && value <= 180;
  const pageHtml = document.documentElement ? (document.documentElement.outerHTML || '') : '';
  const bodyText = String((document.body && document.body.innerText) || '');

  const nodes = Array.from(document.querySelectorAll(
    '[href],[data-href],[data-url],button,[role="button"],[role="link"]'
  ));
  const phoneCandidates = [];
  const addPhone = (value) => {
    const normalized = normalizePhone(value);
    if (normalized && !phoneCandidates.includes(normalized)) phoneCandidates.push(normalized);
  };
  nodes.forEach((node) => {
    const hrefs = [
      node.getAttribute && node.getAttribute('href'),
      node.getAttribute && node.getAttribute('data-href'),
      node.getAttribute && node.getAttribute('data-url')
    ].filter(Boolean);
    hrefs.forEach((href) => {
      if (/^tel:/i.test(href)) addPhone(href);
      const wa = String(href).match(/(?:wa\.me\/|whatsapp(?:\.com)?\/[^?]*\?[^#]*phone=|[?&]phone=)(\+?\d{10,15})/i);
      if (wa) addPhone(wa[1]);
    });
    const outer = node.outerHTML || '';
    (outer.match(/tel:[+0-9(). \-]{8,32}/ig) || []).forEach(addPhone);
    const waOuter = outer.match(/(?:wa\.me\/|phone(?:=|%3D))\+?\d{10,15}/ig) || [];
    waOuter.forEach((value) => addPhone(value.replace(/^.*?(\+?\d{10,15}).*$/, '$1')));
  });
  (pageHtml.match(/tel:[+0-9(). \-]{8,32}/ig) || []).forEach(addPhone);
  const jsonPhonePatterns = [
    /["'](?:phone|phoneNumber|phone_number|mobile|mobileNumber|whatsapp|whatsAppNumber)["']\s*:\s*["']([^"']{8,32})["']/ig,
    /(?:wa\.me\/|[?&]phone=)(\+?\d{10,15})/ig
  ];
  jsonPhonePatterns.forEach((regex) => {
    let match;
    while ((match = regex.exec(pageHtml)) !== null) addPhone(match[1] || match[0]);
  });
  const labeledPhone = bodyText.match(/(?:whats\s*app|telefone|celular|phone|contato)[^+0-9]{0,30}(\+?\d[\d\s().-]{8,22}\d)/i);
  if (labeledPhone) addPhone(labeledPhone[1]);
  const formattedPhone = bodyText.match(/(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?9?\d{4}[-\s]?\d{4}/);
  if (formattedPhone) addPhone(formattedPhone[0]);

  const callAction = nodes.find((node) => {
    const text = clean(node.innerText || node.textContent);
    const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
    const href = clean((node.getAttribute && (node.getAttribute('href') || node.getAttribute('data-href'))) || '');
    return /^tel:/i.test(href) ||
      /wa\.me|whatsapp|[?&]phone=/i.test(href) ||
      /\b(ligar|chamar|telefone|telefonar|whats\s*app|contato)\b/i.test(text + ' ' + label);
  });

  const nameNode = document.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], h1, h2');
  const fareSelectors = [
    '[data-testid*="booking-price"]','[data-testid*="reservation-price"]','[data-testid*="passenger-price"]',
    '[data-testid*="booking-total"]','[data-testid*="reservation-total"]','[data-testid*="price"]',
    '[aria-label*="valor" i]','[aria-label*="preço" i]','[aria-label*="price" i]'
  ];
  const fareNode = document.querySelector(fareSelectors.join(','));
  const currencyNode = fareNode && fareNode.closest('[data-currency], [data-currency-code], [data-testid*="booking"], [data-testid*="reservation"]');
  let fareAmount = clean(fareNode && (
    fareNode.getAttribute('data-value') ||
    fareNode.getAttribute('content') ||
    fareNode.innerText
  ));
  if (!fareAmount) {
    const labeledFare = bodyText.match(/(?:valor\s+que\s+voc[eê]\s+recebe|voc[eê]\s+recebe|receber[aá]|valor\s+da\s+reserva|total\s+da\s+reserva|valor\s+total)[^R$]{0,100}(R\$\s*[0-9.]+(?:,[0-9]{1,2})?)/i);
    fareAmount = clean(labeledFare && labeledFare[1]);
  }
  let fareCurrencyCode = clean(currencyNode && (
    currencyNode.getAttribute('data-currency-code') ||
    currencyNode.getAttribute('data-currency')
  )).toUpperCase();
  if (!fareCurrencyCode && /R\$/.test(fareAmount)) fareCurrencyCode = 'BRL';

  const pickup = { address: '', latitude: null, longitude: null, accuracyMeters: null, source: '' };
  const addressText = (value) => {
    if (!value) return '';
    if (typeof value === 'string') return clean(value);
    if (typeof value !== 'object') return '';
    return clean(
      value.label || value.name || value.formattedAddress || value.formatted_address ||
      value.fullAddress || value.full_address || value.address ||
      [value.street, value.streetNumber || value.number, value.cityName || value.city, value.postalCode || value.zipCode]
        .filter(Boolean).join(', ')
    );
  };
  const readCoordinate = (place) => {
    if (!place || typeof place !== 'object') return null;
    const coordinate = place.coordinates || place.coordinate || place.location || place.geo || place;
    if (!coordinate || typeof coordinate !== 'object') return null;
    const latitude = numberOrNull(coordinate.latitude !== undefined ? coordinate.latitude : coordinate.lat);
    const longitude = numberOrNull(
      coordinate.longitude !== undefined ? coordinate.longitude :
        (coordinate.lng !== undefined ? coordinate.lng : coordinate.lon)
    );
    if (!validLatitude(latitude) || !validLongitude(longitude)) return null;
    const accuracy = numberOrNull(
      coordinate.accuracy !== undefined ? coordinate.accuracy : coordinate.accuracyMeters
    );
    return { latitude, longitude, accuracyMeters: accuracy };
  };
  const acceptPickupPlace = (place, source) => {
    if (!place || typeof place !== 'object') return;
    const address = addressText(place.address || place);
    if (!pickup.address && address) pickup.address = address;
    const coordinate = readCoordinate(place);
    if (coordinate && pickup.latitude === null) {
      pickup.latitude = coordinate.latitude;
      pickup.longitude = coordinate.longitude;
      pickup.accuracyMeters = coordinate.accuracyMeters;
      pickup.source = source;
    }
  };
  const seen = new Set();
  const walk = (value, depth) => {
    if (value === null || value === undefined || depth > 12 || typeof value !== 'object') return;
    if (seen.has(value)) return;
    seen.add(value);
    if (Array.isArray(value)) {
      value.forEach((item) => walk(item, depth + 1));
      return;
    }
    Object.keys(value).forEach((key) => {
      const normalized = key.toLowerCase().replace(/[^a-z]/g, '');
      if (/^(pickupplace|boardingplace|pickuppoint|meetingpoint|departureplace)$/.test(normalized)) {
        acceptPickupPlace(value[key], 'blablacar_booking_structured_pickup');
      }
      walk(value[key], depth + 1);
    });
  };
  const scriptTexts = Array.from(document.querySelectorAll('script'))
    .map((script) => script.textContent || '')
    .filter((text) => /pickup|boarding|meeting|phone|whatsapp/i.test(text));
  scriptTexts.forEach((text) => {
    try { walk(JSON.parse(text), 0); } catch (_) {}
  });
  if (pickup.latitude === null || !pickup.address) {
    scriptTexts.concat([pageHtml]).some((raw) => {
      const marker = raw.search(/pickupPlace|pickup_place|boardingPlace|boarding_place|meetingPoint|meeting_point/i);
      if (marker < 0) return false;
      const slice = raw.slice(marker, marker + 9000);
      if (pickup.latitude === null) {
        const latitudeMatch = slice.match(/["'](?:latitude|lat)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
        const longitudeMatch = slice.match(/["'](?:longitude|lng|lon)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
        const latitude = numberOrNull(latitudeMatch && latitudeMatch[1]);
        const longitude = numberOrNull(longitudeMatch && longitudeMatch[1]);
        if (validLatitude(latitude) && validLongitude(longitude)) {
          pickup.latitude = latitude;
          pickup.longitude = longitude;
          pickup.source = 'blablacar_booking_structured_pickup_text';
          const accuracyMatch = slice.match(/["'](?:accuracy|accuracyMeters)["']\s*:\s*(\d+(?:\.\d+)?)/i);
          pickup.accuracyMeters = numberOrNull(accuracyMatch && accuracyMatch[1]);
        }
      }
      if (!pickup.address) {
        const addressMatch = slice.match(/["'](?:formattedAddress|fullAddress|address|label)["']\s*:\s*["']([^"']{3,300})["']/i);
        if (addressMatch) pickup.address = clean(addressMatch[1]);
      }
      return pickup.latitude !== null || !!pickup.address;
    });
  }
  if (!pickup.address) {
    const pickupNode = document.querySelector(
      '[data-testid*="pickup-address"], [data-testid*="boarding-address"], [data-testid*="pickup-place"], [data-testid*="boarding-place"], [aria-label*="embarque" i], [aria-label*="ponto de encontro" i]'
    );
    pickup.address = clean(pickupNode && (pickupNode.innerText || pickupNode.textContent));
  }

  const clone = document.documentElement.cloneNode(true);
  clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
  clone.querySelectorAll('input, textarea').forEach((node) => {
    node.removeAttribute('value');
    node.textContent = '';
  });
  const html = clone.outerHTML || '';

  return JSON.stringify({
    phone: phoneCandidates[0] || '',
    visibleName: clean(nameNode && nameNode.innerText),
    fareAmount,
    fareCurrencyCode,
    callActionPresent: !!callAction,
    boardingAddress: pickup.address,
    boardingLatitude: pickup.latitude,
    boardingLongitude: pickup.longitude,
    boardingAccuracyMeters: pickup.accuracyMeters,
    boardingLocationSource: pickup.source,
    domHtml: html.slice(0, 350000)
  });
})();
