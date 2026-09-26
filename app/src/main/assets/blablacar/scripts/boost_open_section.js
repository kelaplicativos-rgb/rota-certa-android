(function(){
  const expectedTripId={{EXPECTED_TRIP_ID}};
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const norm=(v)=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const tripEvidence=[location.href||'',...Array.from(document.querySelectorAll('a[href],form[action]')).map(n=>n.href||n.action||'')].join('\n');
  if(!expectedTripId||!tripEvidence.toLowerCase().includes(String(expectedTripId).toLowerCase())) return JSON.stringify({found:false,clicked:false,reason:'trip_identity_not_bound'});
  const nodes=Array.from(document.querySelectorAll('a[href],button,[role="button"],[data-testid]'));
  const score=(n)=>{
    const href=((n.href||'')+' '+((n.getAttribute&&n.getAttribute('href'))||'')).toLowerCase();
    const marker=norm(((n.getAttribute&&n.getAttribute('data-testid'))||'')+' '+((n.getAttribute&&n.getAttribute('data-qa'))||'')+' '+((n.getAttribute&&n.getAttribute('aria-label'))||'')+' '+(n.innerText||n.textContent||''));
    if(/boost/.test(href+' '+marker) && /(booking|reservation|request|reserva|pedido|boost)/.test(href+' '+marker)) return href.includes('boost')?100:80;
    return 0;
  };
  const ranked=nodes.map(n=>({n,s:score(n)})).filter(x=>x.s>0).sort((a,b)=>b.s-a.s);
  if(!ranked.length||ranked.length>1&&ranked[0].s===ranked[1].s) return JSON.stringify({found:false,clicked:false,reason:ranked.length?'boost_section_ambiguous':'boost_section_missing'});
  const target=ranked[0].n;
  if(target.disabled||target.getAttribute('aria-disabled')==='true'||typeof target.click!=='function') return JSON.stringify({found:true,clicked:false,reason:'boost_section_disabled'});
  target.click(); return JSON.stringify({found:true,clicked:true,reason:'boost_section_semantic'});
})();