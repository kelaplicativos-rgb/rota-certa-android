(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const rows=[]; const seen=new Set();
  const candidates=Array.from(document.querySelectorAll('a[href*="passenger"],a[href*="booking"],[data-testid*="passenger"],[data-testid*="booking"],[role="listitem"]'));
  candidates.forEach((node)=>{
    const root=node.closest('li,article,[role="listitem"],[data-testid*="passenger"],[data-testid*="booking"]')||node;
    const lines=(root.innerText||'').split(/\n+/).map(clean).filter(Boolean);
    const route=lines.find((x)=>x.includes('→')||x.includes('->'))||''; if(!route) return;
    const img=root.querySelector('img[alt]');
    const name=clean((img&&img.getAttribute('alt'))||lines[0]||'').replace(/\s*\(\d+\)\s*$/,''); if(!name) return;
    const suffix=(lines.find((x)=>/\(\d+\)\s*$/.test(x))||'').match(/\((\d+)\)\s*$/);
    const seats=suffix?Math.max(1,parseInt(suffix[1],10)||1):1;
    const a=(node.matches&&node.matches('a[href]'))?node:root.querySelector('a[href*="passenger"],a[href*="booking"]');
    const href=a?(a.href||a.getAttribute('href')||''):'';
    const parts=route.split(/→|->/).map(clean); const key=href||[name,seats,route].join('|').toLowerCase();
    if(seen.has(key)) return; seen.add(key);
    rows.push({name,seats,boarding:parts[0]||null,dropoff:parts[parts.length-1]||null,href:href||null});
  });
  const body=clean(document.body&&document.body.innerText);
  const explicitEmpty=/nenhum(?:a)? passageir|sem passageiros?/i.test(body);
  const editPresent=/Editar sua carona/i.test(body)||!!document.querySelector('a[href*="/rides/offer/edit/"]');
  return JSON.stringify({passengers:rows,explicitEmpty,terminalEvidence:editPresent||document.readyState==='complete'});
})();
