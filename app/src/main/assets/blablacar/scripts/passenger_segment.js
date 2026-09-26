(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim(); const body=clean(document.body&&document.body.innerText);
  const route=(body.match(/([^\n]{2,100})\s*(?:→|->)\s*([^\n]{2,100})/)||[]);
  return JSON.stringify({boarding:clean(route[1]||''),dropoff:clean(route[2]||''),url:location.href||''});
})();
