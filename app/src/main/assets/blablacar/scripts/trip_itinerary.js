(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const nodes=Array.from(document.querySelectorAll(
    '[data-testid*="itinerary-departure-station"], [data-testid*="itinerary-stop"], [data-testid*="station"], [data-testid*="itinerary-arrival-station"]'
  ));
  const rows=[];
  nodes.forEach((node)=>{
    const text=clean(node.innerText);
    if(!text) return;
    if(rows.length && rows[rows.length-1].text===text) return;
    rows.push({text:text.slice(0,500)});
  });
  return JSON.stringify({url:location.href||'',stops:rows});
})();
