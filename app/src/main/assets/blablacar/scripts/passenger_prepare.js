(function(){
  const clean=(v)=>String(v||'').replace(/[\u00a0\u202f]/g,' ').replace(/\s+/g,' ').trim().toLowerCase();
  const root=document.querySelector('#root')||document.querySelector('main')||document.body;
  const rootInert=!!(root&&(root.inert===true||root.hasAttribute('inert')));
  if(rootInert){
    return JSON.stringify({clicked:0,controls:0,rootInert:true,url:location.href||''});
  }

  let clicked=0;
  const safeToClick=(node)=>{
    const marker=clean(
      (node.innerText||node.textContent||'')+' '+
      ((node.getAttribute&&node.getAttribute('aria-label'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('title'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('data-testid'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('aria-controls'))||'')
    );
    if(!marker)return false;

    const href=clean((node.getAttribute&&node.getAttribute('href'))||'');
    if(/^tel:|wa\.me|whatsapp|sms:|mailto:/.test(href))return false;

    const destructive=/cancelar|cancel|excluir|delete|recusar|reject|aprovar|approve|pagar|pay|confirmar|confirm|reservar|\bbook\b/;
    const globalControl=/pa[ií]s|country|country code|c[oó]digo do pa[ií]s|idioma|language|moeda|currency|menu|navega[cç][aã]o|navigation/;
    if(destructive.test(marker)||globalControl.test(marker))return false;

    const privateDomain=/(telefone|celular|contato|whats\s*app|valor|pre[cç]o|pagamento|endere[cç]o|embarque|desembarque|ponto\s+de\s+encontro|pickup|dropoff|detalhes\s+da\s+reserva|reservation\s+details|booking\s+details)/;
    if(!privateDomain.test(marker))return false;

    const revealIntent=/(mostrar|exibir|ver\s+|ver$|mais\s+detalhes|detalhes|expand|reveal|show|display|contact|phone)/;
    const explicitlyCollapsed=node.getAttribute&&node.getAttribute('aria-expanded')==='false';
    return revealIntent.test(marker)||explicitlyCollapsed;
  };

  const controls=Array.from(document.querySelectorAll(
    'button,[role="button"],summary,[aria-expanded="false"]'
  )).filter(safeToClick);
  controls.slice(0,8).forEach((node)=>{
    try{node.click();clicked++;}catch(_){}
  });
  try{window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}catch(_){}
  return JSON.stringify({clicked,controls:controls.length,rootInert:false,url:location.href||''});
})();
