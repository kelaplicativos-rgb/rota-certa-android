(function(){
  'use strict';
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const key=(v)=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const absolute=(href)=>{ try { return new URL(href||'', location.href).href; } catch (_) { return href||''; } };

  let tripId='';
  try {
    const url=new URL(location.href);
    tripId=clean(url.searchParams.get('id'));
    if(!tripId){
      const match=url.pathname.match(/\/rides\/offer\/(?!edit(?:\/|$)|passenger(?:\/|$))([^/?#]+)/i);
      tripId=clean(match&&match[1]);
    }
    if(!tripId){
      const match=url.pathname.match(/\/ride-plan\/trip-edit\/([^/?#]+)/i);
      tripId=clean(match&&match[1]);
    }
  } catch (_) {}

  const selectors=[
    '[data-testid="e2e-itinerary-departure-station"]',
    '[data-testid*="itinerary-departure-station"]',
    '[data-testid*="itinerary-stop"]',
    '[data-testid*="itinerary-station"]',
    '[data-testid*="itinerary-arrival-station"]',
    '[data-testid="e2e-itinerary-arrival-station"]'
  ];
  const domStops=[];
  const seenNodes=new Set();
  selectors.forEach((selector)=>{
    Array.from(document.querySelectorAll(selector)).forEach((node)=>{
      if(seenNodes.has(node)) return;
      seenNodes.add(node);
      const text=clean(node.innerText||node.textContent);
      if(!text) return;
      if(domStops.length && key(domStops[domStops.length-1])===key(text)) return;
      domStops.push(text.slice(0,500));
    });
  });

  const networkSource=(tripId && typeof window.__rotaCertaNetworkTripSource==='function')
    ? window.__rotaCertaNetworkTripSource(tripId)
    : null;
  const networkStops=[];
  const rawWaypoints=networkSource && Array.isArray(networkSource.waypoints) ? networkSource.waypoints : [];
  rawWaypoints.slice(0,48).forEach((raw)=>{
    const waypoint=raw&&typeof raw==='object'?raw:{};
    const text=clean(waypoint.label||waypoint.address||'');
    if(!text) return;
    if(networkStops.length && key(networkStops[networkStops.length-1])===key(text)) return;
    networkStops.push(text.slice(0,500));
  });

  const first=(selectors)=>{
    for(const selector of selectors){
      const node=document.querySelector(selector);
      const text=clean(node&&(node.innerText||node.textContent));
      if(text) return text;
    }
    return '';
  };
  const origin=first([
    '[data-testid="e2e-itinerary-departure-station"]',
    '[data-testid*="itinerary-departure-station"]'
  ]);
  const destination=first([
    '[data-testid="e2e-itinerary-arrival-station"]',
    '[data-testid*="itinerary-arrival-station"]'
  ]);
  const samePlace=(a,b)=>{
    const left=key(a), right=key(b);
    return !!left && !!right && (left===right || left.startsWith(right+' ') || right.startsWith(left+' '));
  };
  const authoritative=domStops.length>=2 &&
    samePlace(domStops[0],origin) &&
    samePlace(domStops[domStops.length-1],destination);

  return JSON.stringify({
    url:absolute(location.href||''),
    tripId:tripId,
    domStops:domStops,
    networkStops:networkStops,
    authoritative:authoritative
  });
})();
