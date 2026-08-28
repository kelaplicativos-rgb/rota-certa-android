(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const body=clean(document.body&&document.body.innerText).slice(0,5000);
  const url=location.href||'';
  let state='UNKNOWN';
  if(/\/dashboard\/profile|\/profile\//i.test(url)) state='PROFILE';
  else if(/\/rides\/offer\/edit\//i.test(url)) state=/\/options/i.test(url)?'SEAT_OPTIONS':'TRIP_EDIT';
  else if(/\/rides\/offer\/passenger\//i.test(url)||/\/passenger\//i.test(url)) state='PASSENGER';
  else if(/\/rides\/offer/i.test(url)) state='TRIP_DETAIL';
  else if(/\/rides(?:\?|$|\/)/i.test(url)) state=/arquivad|archived/i.test(body)?'ARCHIVED_RIDES':'RIDE_LIST';
  else if(/search|searchcarpool|blablacar/i.test(url)&&document.querySelector('[data-testid="e2e-srp-card"]')) state='PUBLIC_RESULTS';
  const error=/Ocorreu um erro|Tente novamente|Something went wrong/i.test(body);
  return JSON.stringify({state,url,error,bodyText:body});
})();
