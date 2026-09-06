(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim(); const body=clean(document.body&&document.body.innerText);
  const money=Array.from(body.matchAll(/R\$\s*[0-9.]+(?:,[0-9]{2})?/g)).map((m)=>m[0]);
  const receives=(body.match(/Valor que você recebe\s*(R\$\s*[0-9.]+(?:,[0-9]{2})?)/i)||[])[1]||'';
  const total=(body.match(/Valor total[^R]{0,80}(R\$\s*[0-9.]+(?:,[0-9]{2})?)/i)||[])[1]||'';
  return JSON.stringify({driverReceives:clean(receives),passengerTotal:clean(total),visibleAmounts:money.slice(0,10),url:location.href||''});
})();
