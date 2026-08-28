(function() {
  const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const numberOrNull = (value) => {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  };
  const validLatitude = (value) => value !== null && value >= -90 && value <= 90;
  const validLongitude = (value) => value !== null && value >= -180 && value <= 180;
  const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"], [role="link"]'));
  const callAction = nodes.find((node) => {
    const text = clean(node.innerText || node.textContent);
    const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
    const href = (node.getAttribute && node.getAttribute('href')) || '';
    return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\b(ligar|telefone|telefonar)\b/i.test(label);
  });
  const candidates = [];
  nodes.forEach((node) => {
    const href = (node.getAttribute && node.getAttribute('href')) || '';
    if (/^tel:/i.test(href)) candidates.push(href);
    const outer = node.outerHTML || '';
    const matches = outer.match(/tel:[+0-9(). \-]{8,32}/ig) || [];
    matches.forEach((value) => candidates.push(value));
  });
  const pageHtml = document.documentElement ? (document.documentElement.outerHTML || '') : '';
  (pageHtml.match(/tel:[+0-9(). \-]{8,32}/ig) || []).forEach((value) => candidates.push(value));
  const rawPhone = candidates.find((value) => /^tel:/i.test(value)) || '';
  const phone = rawPhone
    ? rawPhone.replace(/^tel:/i, '').split('?')[0].replace(/[^+0-9]/g, '')
    : '';
  const nameNode = document.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], h1');
  const fareNode = document.querySelector(
    '[data-testid*="booking-price"], [data-testid*="reservation-price"], [data-testid*="passenger-price"], [data-testid*="booking-total"], [data-testid*="reservation-total"]'
  );
  const currencyNode = fareNode && fareNode.closest('[data-currency], [data-currency-code], [data-testid*="booking"], [data-testid*="reservation"]');
  const fareAmount = clean(fareNode && (
    fareNode.getAttribute('data-value') ||
    fareNode.getAttribute('content') ||
    fareNode.innerText
  ));
  const fareCurrencyCode = clean(currencyNode && (
    currencyNode.getAttribute('data-currency-code') ||
    currencyNode.getAttribute('data-currency')
  )).toUpperCase();

  const pickup = {
    address: '',
    latitude: null,
    longitude: null,
    accuracyMeters: null,
    source: ''
  };
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
    const latitude = numberOrNull(
      coordinate.latitude !== undefined ? coordinate.latitude : coordinate.lat
    );
    const longitude = numberOrNull(
      coordinate.longitude !== undefined ? coordinate.longitude :
        (coordinate.lng !== undefined ? coordinate.lng : coordinate.lon)
    );
    if (!validLatitude(latitude) || !validLongitude(longitude)) return null;
    const accuracy = numberOrNull(
      coordinate.accuracy !== undefined ? coordinate.accuracy : coordinate.accuracyMeters
    );
    return { latitude: latitude, longitude: longitude, accuracyMeters: accuracy };
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
    if (value === null || value === undefined || depth > 10) return;
    if (typeof value !== 'object') return;
    if (seen.has(value)) return;
    seen.add(value);
    if (Array.isArray(value)) {
      value.forEach((item) => walk(item, depth + 1));
      return;
    }
    Object.keys(value).forEach((key) => {
      const normalized = key.toLowerCase().replace(/[^a-z]/g, '');
      if (normalized === 'pickupplace' || normalized === 'boardingplace' || normalized === 'pickuppoint') {
        acceptPickupPlace(value[key], 'blablacar_booking_structured_pickup');
      }
      walk(value[key], depth + 1);
    });
  };
  const scriptTexts = Array.from(document.querySelectorAll('script'))
    .map((script) => script.textContent || '')
    .filter((text) => /pickup|boarding/i.test(text));
  scriptTexts.forEach((text) => {
    try {
      walk(JSON.parse(text), 0);
    } catch (_) {
      // Framework bootstrap scripts are often JavaScript rather than pure JSON.
    }
  });
  if (pickup.latitude === null) {
    scriptTexts.concat([pageHtml]).some((raw) => {
      const marker = raw.search(/pickupPlace|pickup_place|boardingPlace|boarding_place/i);
      if (marker < 0) return false;
      const slice = raw.slice(marker, marker + 6000);
      const latitudeMatch = slice.match(/["'](?:latitude|lat)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
      const longitudeMatch = slice.match(/["'](?:longitude|lng|lon)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
      const latitude = numberOrNull(latitudeMatch && latitudeMatch[1]);
      const longitude = numberOrNull(longitudeMatch && longitudeMatch[1]);
      if (!validLatitude(latitude) || !validLongitude(longitude)) return false;
      pickup.latitude = latitude;
      pickup.longitude = longitude;
      pickup.source = 'blablacar_booking_structured_pickup_text';
      const accuracyMatch = slice.match(/["'](?:accuracy|accuracyMeters)["']\s*:\s*(\d+(?:\.\d+)?)/i);
      pickup.accuracyMeters = numberOrNull(accuracyMatch && accuracyMatch[1]);
      if (!pickup.address) {
        const addressMatch = slice.match(/["'](?:formattedAddress|fullAddress|address)["']\s*:\s*["']([^"']{3,240})["']/i);
        if (addressMatch) pickup.address = clean(addressMatch[1]);
      }
      return true;
    });
  }
  if (!pickup.address) {
    const pickupNode = document.querySelector(
      '[data-testid*="pickup-address"], [data-testid*="boarding-address"], [data-testid*="pickup-place"], [data-testid*="boarding-place"]'
    );
    pickup.address = clean(pickupNode && pickupNode.innerText);
  }
  const clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
clone.querySelectorAll('input, textarea').forEach((node) => {
  node.removeAttribute('value');
  node.textContent = '';
});
const html = clone.outerHTML || '';
  return JSON.stringify({
    phone: phone,
    visibleName: clean(nameNode && nameNode.innerText),
    fareAmount: fareAmount,
    fareCurrencyCode: fareCurrencyCode,
    callActionPresent: !!callAction,
    boardingAddress: pickup.address,
    boardingLatitude: pickup.latitude,
    boardingLongitude: pickup.longitude,
    boardingAccuracyMeters: pickup.accuracyMeters,
    boardingLocationSource: pickup.source,
    domHtml: html.slice(0, 350000)
  });
})();
