(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const target=Array.from(document.querySelectorAll('button,[role="button"]')).find((n)=>/^Salvar$/i.test(clean(n.innerText))||/save|submit/i.test(((n.getAttribute&&n.getAttribute('data-testid'))||'')+' '+((n.getAttribute&&n.getAttribute('aria-label'))||'')));
  if(!target||target.disabled||typeof target.click!=='function') return JSON.stringify({found:false,clicked:false});
  target.click(); return JSON.stringify({found:true,clicked:true});
})();
