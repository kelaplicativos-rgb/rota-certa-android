(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const uuid=/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig;
  const body=clean(document.body&&document.body.innerText);
  const nameNode=document.querySelector('[data-testid*="passenger-name"],[data-testid*="profile-name"],h1,h2,img[alt]');
  const img=document.querySelector('img[alt][src]');
  const ids=Array.from(new Set(((location.href+' '+document.documentElement.outerHTML).match(uuid)||[]).map((x)=>x.toLowerCase())));
  return JSON.stringify({name:clean((nameNode&&((nameNode.innerText)||(nameNode.getAttribute&&nameNode.getAttribute('alt'))))||''),photoUrl:img?(img.currentSrc||img.src||''):'',observedUuids:ids,url:location.href||'',bodyText:body.slice(0,3000)});
})();
