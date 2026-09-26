(function() {
  const wantedIndex={{PASSENGER_INDEX}};
  const clean=(v)=>String(v||'').replace(/\s+/g,' ').trim();
  const linesOf=(node)=>String((node&&node.innerText)||'').split(/\n+/).map(clean).filter(Boolean);
  const containers=Array.from(document.querySelectorAll(
    'li,article,[role="listitem"],[data-testid*="passenger"],[data-testid*="booking"],[data-testid*="reservation"]'
  ));
  const cards=[];
  const seen=new Set();

  const candidateFor=(container)=>{
    const lines=linesOf(container);
    const route=lines.find((line)=>line.includes('→')||line.includes('->'))||'';
    const explicit=container.querySelector&&container.querySelector(
      '[data-testid*="passenger-name"],[data-testid*="profile-name"],img[alt],h3,h4'
    );
    const marker=clean(
      ((container.getAttribute&&container.getAttribute('data-testid'))||'')+' '+
      ((container.getAttribute&&container.getAttribute('aria-label'))||'')
    ).toLowerCase();
    const passengerMarked=/passenger|booking|reservation|passageir|reserva/i.test(marker);
    if(!route&&!passengerMarked)return null;
    const anchors=Array.from(container.querySelectorAll?container.querySelectorAll('a[href],button,[role="link"],[role="button"]'):[]);
    const scored=anchors.map((node)=>{
      const href=clean((node.getAttribute&&node.getAttribute('href'))||'');
      const text=clean(node.innerText||node.textContent||node.getAttribute&&node.getAttribute('aria-label'));
      let score=0;
      if(/\/rides\/offer\/passenger\//i.test(href))score+=100;
      if(/\/booking\//i.test(href))score+=80;
      if(/passenger|booking|reservation/i.test(href))score+=40;
      if(/detalhes|reserva|passageir|booking|viagem/i.test(text))score+=20;
      if(/^https?:/i.test(href)||href.startsWith('/'))score+=5;
      if(/profile|perfil/i.test(href+text)&&!/booking|reservation|reserva/i.test(href+text))score-=40;
      return {node,href,score};
    }).sort((a,b)=>b.score-a.score);
    const best=scored[0];
    const alt=explicit&&explicit.getAttribute?clean(explicit.getAttribute('alt')):'';
    const name=clean(alt||(explicit&&explicit.innerText)||lines[0]||'');
    const suffixSource=lines.find((line)=>/\(\d+\)\s*$/.test(line))||name;
    const suffix=suffixSource.match(/\((\d+)\)\s*$/);
    const seats=suffix?Math.max(1,parseInt(suffix[1],10)||1):1;
    const key=(best&&best.href)||[name.toLowerCase(),seats,route].join('|');
    if(!key||seen.has(key))return null;
    seen.add(key);
    return {container,clickable:best&&best.node,name,route,seats,score:best?best.score:0};
  };

  containers.forEach((container)=>{const card=candidateFor(container);if(card)cards.push(card);});
  if(cards.length===0){
    Array.from(document.querySelectorAll('a[href*="/rides/offer/passenger/"],a[href*="/booking/"]')).forEach((anchor)=>{
      const container=(anchor.closest&&anchor.closest('li,article,[role="listitem"]'))||anchor;
      const card=candidateFor(container);
      if(card)cards.push(card);
    });
  }

  const item=cards[wantedIndex];
  if(!item)return JSON.stringify({found:false,clicked:false,candidates:cards.length});
  let clickable=item.clickable;
  if(!clickable&&item.container){
    clickable=item.container.querySelector&&item.container.querySelector(
      'a[href*="/rides/offer/passenger/"],a[href*="/booking/"],a[href],button,[role="link"],[role="button"]'
    );
  }
  if(!clickable||typeof clickable.click!=='function')return JSON.stringify({found:true,clicked:false,candidates:cards.length});
  try{
    clickable.scrollIntoView({block:'center',inline:'nearest'});
  }catch(_){}
  clickable.click();
  return JSON.stringify({found:true,clicked:true,candidates:cards.length});
})();
