(function() {
  const text=(root,selector)=>root.querySelector(selector)?.textContent?.trim()||null;
  const cards=Array.from(document.querySelectorAll('[data-testid="e2e-srp-card"]')).map((card)=>({
    driverName:text(card,'[data-testid="e2e-tripcard-driver-name"]')||'',
    departureTime:text(card,'[data-testid="e2e-itinerary-departure-time"]'),
    arrivalTime:text(card,'[data-testid="e2e-itinerary-arrival-time"]'),
    actualDeparture:text(card,'[data-testid="e2e-itinerary-departure-station"]'),
    actualArrival:text(card,'[data-testid="e2e-itinerary-arrival-station"]'),
    priceText:text(card,'[data-testid="e2e-tripcard-price"]'),
    ratingText:text(card,'[data-testid*="rating"]'),
    seatsText:text(card,'[data-testid*="seat"]')||text(card,'[data-testid*="availability"]'),
    text:card.innerText||'',
    href:card.querySelector('a[href*="/trip"]')?.getAttribute('href')||null
  }));
  return JSON.stringify({bodyText:document.body?.innerText||'',cards});
})();
