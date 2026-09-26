(function(){
  const clean=(v)=>String(v||'').replace(/\s+/g,' ').trim().toLowerCase();
  let clicked=0;
  const safeToClick=(node)=>{
    const marker=clean(
      (node.innerText||node.textContent||'')+' '+
      ((node.getAttribute&&node.getAttribute('aria-label'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('title'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('data-testid'))||'')
    );
    if(!marker)return false;
    if(/cancelar|cancel|excluir|delete|recusar|reject|aprovar|approve|pagar|pay|confirmar|confirm|reservar|book/.test(marker))return false;
    const href=clean((node.getAttribute&&node.getAttribute('href'))||'');
    if(/^tel:|wa\.me|whatsapp|sms:|mailto:/.test(href))return false;
    return (
      (node.getAttribute&&node.getAttribute('aria-expanded')==='false') ||
      /ver\s+mais|mostrar|exibir|detalhes|mais\s+detalhes|contato|telefone|celular|valor|pre[cç]o|pagamento|endere[cç]o|embarque|desembarque|ponto\s+de\s+encontro|pickup|dropoff/.test(marker)
    );
  };
  const controls=Array.from(document.querySelectorAll(
    'button,[role="button"],summary,[aria-expanded="false"]'
  )).filter(safeToClick);
  controls.slice(0,12).forEach((node)=>{
    try{node.click();clicked++;}catch(_){}
  });
  try{window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}catch(_){}
  return JSON.stringify({clicked,controls:controls.length,url:location.href||''});
})();
