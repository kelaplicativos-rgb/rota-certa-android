(function(){
  const clean=(v)=>String(v||'').replace(/[\u00a0\u202f]/g,' ').replace(/\s+/g,' ').trim();
  const rawText=String((document.body&&document.body.innerText)||'');
  const body=clean(rawText);
  const html=String((document.documentElement&&document.documentElement.outerHTML)||'');
  const amount='(?:[0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,[0-9]{1,2})?';
  const moneyToken='(?:R\\$\\s*'+amount+'|'+amount+'\\s*R\\$)';
  const moneyRegex=new RegExp(moneyToken,'gi');
  const unique=(values)=>Array.from(new Set(values.map(clean).filter(Boolean)));
  const collectMoney=(text)=>(clean(text).match(moneyRegex)||[]).map(clean);
  const visibleAmounts=unique([
    ...collectMoney(body),
    ...collectMoney(html)
  ]).slice(0,20);

  const around=(labels)=>{
    for(const label of labels){
      const pattern=new RegExp(label+'[^0-9R$]{0,140}('+moneyToken+')','i');
      const match=body.match(pattern)||html.match(pattern);
      if(match&&match[1])return clean(match[1]);
    }
    return '';
  };

  const nodes=Array.from(document.querySelectorAll(
    '[data-testid*="price"],[data-testid*="fare"],[data-testid*="total"],[data-testid*="amount"],'+
    '[aria-label*="valor" i],[aria-label*="preço" i],[aria-label*="price" i],[data-currency],[data-currency-code]'
  ));
  nodes.forEach((node)=>{
    const text=clean(
      (node.getAttribute&&(
        node.getAttribute('data-value')||
        node.getAttribute('content')||
        node.getAttribute('aria-label')
      ))||
      node.innerText||node.textContent
    );
    collectMoney(text).forEach((value)=>{
      if(!visibleAmounts.includes(value))visibleAmounts.push(value);
    });
  });

  const driverReceives=around([
    'valor\\s+que\\s+voc[eê]\\s+recebe',
    'voc[eê]\\s+recebe',
    'voc[eê]\\s+vai\\s+receber',
    'receber[aá]',
    'ganho(?:s)?',
    'seu\\s+recebimento'
  ]);
  const passengerTotal=around([
    'valor\\s+total',
    'total\\s+da\\s+reserva',
    'valor\\s+da\\s+reserva',
    'pre[cç]o\\s+da\\s+reserva',
    'valor\\s+pago',
    'total\\s+pago'
  ]);

  return JSON.stringify({
    driverReceives,
    passengerTotal,
    visibleAmounts:unique(visibleAmounts).slice(0,20),
    url:location.href||''
  });
})();
