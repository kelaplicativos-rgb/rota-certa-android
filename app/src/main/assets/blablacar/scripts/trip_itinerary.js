(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  const nodes=Array.from(document.querySelectorAll('[data-testid*="itinerary"], [data-testid*="station"], time[datetime]'));
  const rows=[]; const seen=new Set();
  nodes.forEach((n)=>{
    const root=n.closest('li,article,[role="listitem"],div')||n;
    const text=clean(root.innerText); if(!text||seen.has(text)) return;
    if(!/\d{1,2}:\d{2}|SP|MG|Santo André|São Paulo|Extrema|Pouso Alegre|Três Corações|São Thomé/i.test(text)) return;
    seen.add(text); rows.push({text:text.slice(0,500)});
  });
  return JSON.stringify({url:location.href||'',stops:rows});
})();
