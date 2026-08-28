(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const body=clean(document.body&&document.body.innerText);
  const addressNodes=Array.from(document.querySelectorAll('[data-testid*="address"],[data-testid*="pickup"],[data-testid*="dropoff"],[aria-label*="endereço" i]')).map((n)=>clean(n.innerText||n.getAttribute('aria-label'))).filter(Boolean);
  return JSON.stringify({specificAddresses:Array.from(new Set(addressNodes)).slice(0,10),hasSpecificAddress:addressNodes.length>0,url:location.href||'',bodyText:body.slice(0,2500)});
})();
