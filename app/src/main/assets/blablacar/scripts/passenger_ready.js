(function(){
  const clean=(v)=>String(v||'').replace(/[\u00a0\u202f]/g,' ').replace(/\s+/g,' ').trim();
  const root=document.querySelector('#root')||document.querySelector('main')||document.body;
  const rootInert=!!(root&&(root.inert===true||root.hasAttribute('inert')));
  const body=clean((document.body&&document.body.innerText)||'');
  const html=String((document.documentElement&&document.documentElement.outerHTML)||'');
  const amount='(?:[0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,[0-9]{1,2})?';
  const money=new RegExp('(?:R\\$\\s*'+amount+'|'+amount+'\\s*R\\$)','i').test(body);
  const route=/(?:→|->)/.test(body);
  const phone=/href=["']tel:/i.test(html)||/(?:telefone|celular|whats\s*app|contato)/i.test(body);
  const reservation=/(?:valor\s+da\s+reserva|total\s+da\s+reserva|passageir[oa]|reserva|perfil\s+verificado)/i.test(body);
  const markerCount=[money,route,phone,reservation].filter(Boolean).length;
  const ready=document.readyState==='complete'&&!rootInert&&body.length>=60&&(route||money||phone)&&markerCount>=1;
  return JSON.stringify({ready,rootInert,markerCount,bodyLength:body.length,url:location.href||''});
})();
