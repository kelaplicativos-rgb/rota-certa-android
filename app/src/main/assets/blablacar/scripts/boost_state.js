(function() {
  const expectedTripId={{EXPECTED_TRIP_ID}};
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const norm=(v)=>clean(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const attr=(n,k)=>(n&&n.getAttribute&&n.getAttribute(k))||'';
  const text=(n)=>norm((n&&(n.innerText||n.textContent))||'');
  const marker=(n)=>norm([attr(n,'data-testid'),attr(n,'data-qa'),attr(n,'id'),attr(n,'name'),attr(n,'aria-label'),attr(n,'title'),text(n)].join(' '));
  const tripEvidence=[location.href||'', ...Array.from(document.querySelectorAll('a[href],form[action],[data-href]')).map(n=>attr(n,'href')||attr(n,'action')||attr(n,'data-href'))].join('\n');
  const identityBound=!!expectedTripId && tripEvidence.toLowerCase().includes(String(expectedTripId).toLowerCase());
  const body=norm(document.body&&document.body.innerText);
  const authRequired=!!document.querySelector('input[type="password"]') || /\b(login|sign in|entrar|connexion|iniciar sesion|anmelden)\b/.test(norm(location.pathname));
  const pageError=/\b(temporarily unavailable|something went wrong|erro inesperado|page not found|access denied)\b/.test(body);
  const controls=Array.from(document.querySelectorAll('input[type="checkbox"],input[type="radio"],[role="switch"],[role="checkbox"],[aria-checked]'));
  const associated=(n)=>{
    let out=marker(n);
    if(n&&n.id){ const label=document.querySelector('label[for="'+CSS.escape(n.id)+'"]'); if(label) out+=' '+text(label); }
    let p=n&&n.parentElement, depth=0;
    while(p&&depth++<4){ out+=' '+marker(p); p=p.parentElement; }
    return norm(out);
  };
  const isBoost=(n)=>{
    const m=associated(n);
    if(/(^|\W)boost(\W|$)/.test(m)) return true;
    return /(booking|reservation|reserva|pedido|request)/.test(m) && /boost/.test(m);
  };
  const boostControls=controls.filter(isBoost);
  const unique=boostControls.length===1?boostControls[0]:null;
  const readChecked=(n)=>{
    if(!n) return null;
    if(typeof n.checked==='boolean') return n.checked;
    const a=attr(n,'aria-checked').toLowerCase(); if(a==='true') return true; if(a==='false') return false;
    const s=attr(n,'data-state').toLowerCase(); if(/checked|on|active|enabled/.test(s)) return true; if(/unchecked|off|inactive|disabled/.test(s)) return false;
    return null;
  };
  const checked=readChecked(unique);
  const state=checked===true?'ENABLED':checked===false?'DISABLED':'UNKNOWN';
  const saveNodes=Array.from(document.querySelectorAll('button[type="submit"],button,[role="button"],[data-testid*="save" i],[data-testid*="submit" i]'));
  const savePresent=saveNodes.some(n=>!/^(true)$/i.test(String(n.disabled)) && (/save|submit/.test(norm(attr(n,'data-testid')+' '+attr(n,'aria-label')+' '+attr(n,'name'))) || /^(save|salvar|enregistrer|guardar|speichern|salva)$/i.test(clean(n.innerText||n.textContent))));
  const href=norm(location.href);
  let screen='RIDE_DETAIL';
  if(authRequired) screen='AUTH_REQUIRED';
  else if(pageError) screen='ERROR';
  else if(unique) screen='BOOST_EDIT';
  else if(/\/rides\/offer\/edit/.test(href) || document.querySelector('[data-testid*="edit" i],[href*="/rides/offer/edit"]')) screen='RIDE_EDIT';
  else if(!identityBound) screen='UNKNOWN';
  return JSON.stringify({screen:screen,tripIdBound:identityBound,controlFound:!!unique,controlAmbiguous:boostControls.length>1,state:state,savePresent:savePresent,authRequired:authRequired,error:pageError,pageUrl:(location.href||'').slice(0,1200)});
})();