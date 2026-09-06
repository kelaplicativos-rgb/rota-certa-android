(function(){
  const direction={{DIRECTION}}; const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const buttons=Array.from(document.querySelectorAll('button,[role="button"]'));
  const mark=(n)=>clean(((n.getAttribute&&n.getAttribute('aria-label'))||'')+' '+(n.innerText||'')).toLowerCase();
  const target=direction>0?buttons.find((n)=>/adicionar|increment|increase|plus/.test(mark(n))||/^\+$/.test(clean(n.innerText))):buttons.find((n)=>/remover|decrement|decrease|minus/.test(mark(n))||/^[−–-]$/.test(clean(n.innerText)));
  if(!target||target.disabled||typeof target.click!=='function') return JSON.stringify({found:false,clicked:false});
  target.click(); return JSON.stringify({found:true,clicked:true,direction});
})();
