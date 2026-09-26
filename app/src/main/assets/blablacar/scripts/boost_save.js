(function(){
  const expectedTripId={{EXPECTED_TRIP_ID}};
  const desired={{DESIRED_ENABLED}};
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const norm=(v)=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const attr=(n,k)=>(n&&n.getAttribute&&n.getAttribute(k))||'';
  const evidence=[location.href||'',...Array.from(document.querySelectorAll('a[href],form[action],[data-href]')).map(n=>attr(n,'href')||attr(n,'action')||attr(n,'data-href'))].join('\n');
  if(!expectedTripId||!evidence.toLowerCase().includes(String(expectedTripId).toLowerCase())) return JSON.stringify({found:false,clicked:false,reason:'trip_identity_not_bound'});
  const controls=Array.from(document.querySelectorAll('input[type="checkbox"],input[type="radio"],[role="switch"],[role="checkbox"],[aria-checked]'));
  const marker=(n)=>{let out=norm([attr(n,'data-testid'),attr(n,'data-qa'),attr(n,'id'),attr(n,'name'),attr(n,'aria-label'),attr(n,'title'),n.innerText||n.textContent||''].join(' '));if(n&&n.id){const l=document.querySelector('label[for="'+CSS.escape(n.id)+'"]');if(l)out+=' '+norm(l.innerText||l.textContent);}let p=n&&n.parentElement,d=0;while(p&&d++<4){out+=' '+norm([attr(p,'data-testid'),attr(p,'aria-label'),p.innerText||''].join(' '));p=p.parentElement;}return norm(out);};
  const candidates=controls.filter(n=>/(^|\W)boost(\W|$)/.test(marker(n)) || (/(booking|reservation|reserva|pedido|request)/.test(marker(n))&&/boost/.test(marker(n))));
  if(candidates.length!==1) return JSON.stringify({found:false,clicked:false,reason:candidates.length?'boost_control_ambiguous':'boost_control_missing'});
  const boost=candidates[0];
  let current=null;if(typeof boost.checked==='boolean')current=boost.checked;else{const a=attr(boost,'aria-checked').toLowerCase();if(a==='true')current=true;if(a==='false')current=false;}
  if(current===null||current!==desired) return JSON.stringify({found:false,clicked:false,reason:'desired_state_not_selected'});
  const nodes=Array.from(document.querySelectorAll('button[type="submit"],button,[role="button"],[data-testid*="save" i],[data-testid*="submit" i]')).filter(n=>!n.disabled&&attr(n,'aria-disabled')!=='true');
  const scored=nodes.map(n=>{const stable=norm(attr(n,'data-testid')+' '+attr(n,'data-qa')+' '+attr(n,'name')+' '+attr(n,'aria-label'));const label=clean(n.innerText||n.textContent);let s=0;if(/save|submit/.test(stable))s=100;else if(n.type==='submit')s=80;else if(/^(save|salvar|enregistrer|guardar|speichern|salva)$/i.test(label))s=50;return {n,s};}).filter(x=>x.s>0).sort((a,b)=>b.s-a.s);
  if(!scored.length||scored.length>1&&scored[0].s===scored[1].s) return JSON.stringify({found:false,clicked:false,reason:scored.length?'save_ambiguous':'save_missing'});
  if(typeof scored[0].n.click!=='function') return JSON.stringify({found:true,clicked:false,reason:'save_not_clickable'});
  scored[0].n.click(); return JSON.stringify({found:true,clicked:true,reason:'save_submitted'});
})();