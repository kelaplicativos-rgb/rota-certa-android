(function(){
  const clean=(v)=>String(v||'').replace(/[\u00a0\u202f]/g,' ').replace(/\s+/g,' ').trim();
  const raw=String((document.body&&document.body.innerText)||'');
  const candidates=[];
  raw.split(/\n+/).map(clean).filter(Boolean).forEach((line)=>{
    if((line.includes('→')||line.includes('->'))&&line.length<=320)candidates.push(line);
  });
  Array.from(document.querySelectorAll('h1,h2,h3,p,span,div,[data-testid],[aria-label]')).forEach((node)=>{
    const text=clean(node.innerText||node.textContent);
    if((text.includes('→')||text.includes('->'))&&text.length>=5&&text.length<=320)candidates.push(text);
  });
  const ordered=Array.from(new Set(candidates)).sort((a,b)=>a.length-b.length);
  let boarding='',dropoff='';
  for(const value of ordered){
    const match=value.match(/^(.{2,140}?)\s*(?:→|->)\s*(.{2,140}?)$/);
    if(!match)continue;
    boarding=clean(match[1]);
    dropoff=clean(match[2]);
    if(boarding&&dropoff)break;
  }
  return JSON.stringify({boarding,dropoff,url:location.href||''});
})();
