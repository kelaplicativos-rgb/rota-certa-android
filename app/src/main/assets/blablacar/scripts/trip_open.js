(function(){
  const wanted={{TRIP_INDEX}};
  const cards=Array.from(document.querySelectorAll('a[href], [role="link"]')).filter((n)=>{
    const h=(n.href||n.getAttribute('href')||'');
    return /\/rides\/offer\//i.test(h)&&!/\/edit\//i.test(h)&&!/\/passenger\//i.test(h);
  });
  const node=cards[wanted];
  if(!node||typeof node.click!=='function') return JSON.stringify({found:false,clicked:false});
  const href=node.href||node.getAttribute('href')||''; node.click();
  return JSON.stringify({found:true,clicked:true,href});
})();
