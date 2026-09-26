(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const body=clean(document.body&&document.body.innerText);
  const nodes=Array.from(document.querySelectorAll(
    '[data-testid*="address"],[data-testid*="pickup"],[data-testid*="dropoff"],[data-testid*="boarding"],[aria-label*="endereço" i],[aria-label*="embarque" i],[aria-label*="desembarque" i]'
  ));
  const values=[];
  let boardingAddress='';
  let dropoffAddress='';
  nodes.forEach((node)=>{
    const text=clean(node.innerText||node.textContent||node.getAttribute('aria-label'));
    if(!text)return;
    values.push(text);
    const marker=clean(
      ((node.getAttribute&&node.getAttribute('data-testid'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('aria-label'))||'')
    ).toLowerCase();
    if(!boardingAddress&&/(pickup|boarding|embarque|partida)/i.test(marker))boardingAddress=text;
    if(!dropoffAddress&&/(dropoff|desembarque|arrival|destino)/i.test(marker))dropoffAddress=text;
  });
  const addressNodes=Array.from(new Set(values)).slice(0,10);
  return JSON.stringify({
    specificAddresses:addressNodes,
    hasSpecificAddress:addressNodes.length>0,
    boardingAddress:boardingAddress,
    dropoffAddress:dropoffAddress,
    url:location.href||'',
    bodyText:body.slice(0,2500)
  });
})();
