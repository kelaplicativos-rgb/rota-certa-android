(function() {
  const text=(root,selector)=>root.querySelector(selector)?.textContent?.trim()||null;
  const firstText=(root,selectors)=>{
    for (const selector of selectors) {
      const value=text(root,selector);
      if (value) return value;
    }
    return null;
  };
  const cards=Array.from(document.querySelectorAll('[data-testid="e2e-srp-card"]')).map((card,cardIndex)=>{
    const profileAnchors=Array.from(card.querySelectorAll(
      'a[href*="/profile"],a[href*="/member"],a[href*="/user"]'
    ));
    const profileName=profileAnchors
      .map((anchor)=>anchor.textContent?.trim()||'')
      .find((value)=>value.length>0)||'';
    return {
      cardIndex,
      driverName:firstText(card,[
        '[data-testid="e2e-tripcard-driver-name"]',
        '[data-testid*="tripcard-driver-name"]'
      ])||profileName,
      departureTime:firstText(card,[
        '[data-testid="e2e-itinerary-departure-time"]',
        '[data-testid*="itinerary-departure-time"]'
      ]),
      arrivalTime:firstText(card,[
        '[data-testid="e2e-itinerary-arrival-time"]',
        '[data-testid*="itinerary-arrival-time"]'
      ]),
      actualDeparture:firstText(card,[
        '[data-testid="e2e-itinerary-departure-station"]',
        '[data-testid*="itinerary-departure-station"]'
      ]),
      actualArrival:firstText(card,[
        '[data-testid="e2e-itinerary-arrival-station"]',
        '[data-testid*="itinerary-arrival-station"]'
      ]),
      priceText:firstText(card,[
        '[data-testid="e2e-tripcard-price"]',
        '[data-testid*="tripcard-price"]'
      ]),
      ratingText:text(card,'[data-testid*="rating"]'),
      seatsText:text(card,'[data-testid*="seat"]')||text(card,'[data-testid*="availability"]'),
      text:card.innerText||'',
      href:card.querySelector('a[href*="/trip"]')?.getAttribute('href')||null,
      profileHrefs:profileAnchors
        .map((a)=>a.getAttribute('href'))
        .filter((href)=>href)
    };
  });
  const root=document.scrollingElement||document.documentElement;
  const scrollHeight=Math.max(root?.scrollHeight||0,document.body?.scrollHeight||0);
  const atBottom=((window.scrollY||root?.scrollTop||0)+window.innerHeight)>=scrollHeight-48;
  const loadingIndicatorPresent=!!document.querySelector('[aria-busy="true"],[data-testid*="loading"],[data-testid*="loader"]');
  return JSON.stringify({bodyText:document.body?.innerText||'',cards,atBottom,loadingIndicatorPresent,scrollHeight});
})();