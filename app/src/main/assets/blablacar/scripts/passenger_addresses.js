(function(){
  const clean=(v)=>String(v||'').replace(/\s+/g,' ').trim();
  const rawText=String((document.body&&document.body.innerText)||'');
  const html=String((document.documentElement&&document.documentElement.outerHTML)||'');
  const lines=rawText.split(/\n+/).map(clean).filter(Boolean);
  const values=[];
  const add=(value)=>{
    const text=clean(value);
    if(text.length>=5&&text.length<=500&&!values.includes(text))values.push(text);
  };
  let boardingAddress='';
  let dropoffAddress='';

  const classify=(marker,text)=>{
    const normalized=clean(marker).toLowerCase();
    if(!boardingAddress&&/(pickup|boarding|embarque|partida|sa[ií]da|ponto\s+de\s+encontro|meeting\s*point)/i.test(normalized)) {
      boardingAddress=clean(text);
    }
    if(!dropoffAddress&&/(dropoff|desembarque|arrival|destino|chegada)/i.test(normalized)) {
      dropoffAddress=clean(text);
    }
  };

  const nodes=Array.from(document.querySelectorAll(
    '[data-testid*="address"],[data-testid*="pickup"],[data-testid*="dropoff"],[data-testid*="boarding"],'+
    '[data-testid*="meeting"],[aria-label*="endereço" i],[aria-label*="embarque" i],'+
    '[aria-label*="desembarque" i],[aria-label*="destino" i],[aria-label*="ponto de encontro" i],address'
  ));
  nodes.forEach((node)=>{
    const text=clean(node.innerText||node.textContent||node.getAttribute('aria-label'));
    if(!text)return;
    add(text);
    const marker=clean(
      ((node.getAttribute&&node.getAttribute('data-testid'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('aria-label'))||'')+' '+
      ((node.getAttribute&&node.getAttribute('title'))||'')
    );
    classify(marker,text);
  });

  const lineAfter=(regex)=>{
    const index=lines.findIndex((line)=>regex.test(line));
    if(index<0)return '';
    const same=lines[index].replace(regex,'').replace(/^[:\-–—\s]+/,'').trim();
    if(same.length>=5)return same;
    for(let offset=1;offset<=3;offset++){
      const candidate=lines[index+offset]||'';
      if(candidate.length>=5&&!/(editar|alterar|ver mapa|mapa|como chegar)/i.test(candidate))return candidate;
    }
    return '';
  };
  if(!boardingAddress)boardingAddress=lineAfter(/^(?:local|endere[cç]o|ponto)?\s*(?:de\s*)?(?:embarque|partida|sa[ií]da|encontro)\b\s*/i);
  if(!dropoffAddress)dropoffAddress=lineAfter(/^(?:local|endere[cç]o|ponto)?\s*(?:de\s*)?(?:desembarque|destino|chegada)\b\s*/i);
  add(boardingAddress); add(dropoffAddress);

  const structured=[];
  const seen=new Set();
  const addressText=(value)=>{
    if(!value)return '';
    if(typeof value==='string')return clean(value);
    if(typeof value!=='object')return '';
    return clean(
      value.formattedAddress||value.formatted_address||value.fullAddress||value.full_address||
      value.addressLine||value.address_line||value.label||value.name||value.address||
      [value.street||value.streetName,value.streetNumber||value.number,value.neighborhood||value.district,value.cityName||value.city,value.state,value.postalCode||value.zipCode]
        .filter(Boolean).join(', ')
    );
  };
  const walk=(value,depth)=>{
    if(value===null||value===undefined||depth>12||typeof value!=='object')return;
    if(seen.has(value))return;
    seen.add(value);
    if(Array.isArray(value)){value.forEach((item)=>walk(item,depth+1));return;}
    Object.keys(value).forEach((key)=>{
      const normalized=key.toLowerCase().replace(/[^a-z]/g,'');
      if(/pickup|boarding|departure|meetingpoint|dropoff|arrival|destination/.test(normalized)){
        const text=addressText(value[key]);
        if(text){
          structured.push({key:normalized,text});
          add(text);
          classify(normalized,text);
        }
      }
      walk(value[key],depth+1);
    });
  };
  Array.from(document.querySelectorAll('script')).forEach((script)=>{
    const text=script.textContent||'';
    if(!/(pickup|boarding|departure|meeting|dropoff|arrival|destination|address)/i.test(text))return;
    try{walk(JSON.parse(text),0);}catch(_){}
  });

  const scanText=(labelRegex,target)=>{
    const marker=html.search(labelRegex);
    if(marker<0)return target;
    const slice=html.slice(marker,marker+10000);
    const patterns=[
      /["'](?:formattedAddress|fullAddress|addressLine|address|label)["']\s*:\s*["']([^"']{5,500})["']/i,
      /(?:endere[cç]o|address)[^<]{0,80}<[^>]+>\s*([^<]{5,300})</i
    ];
    for(const pattern of patterns){
      const m=slice.match(pattern);
      if(m&&m[1])return clean(m[1]);
    }
    return target;
  };
  if(!boardingAddress)boardingAddress=scanText(/pickupPlace|boardingPlace|departurePlace|meetingPoint|embarque|ponto de encontro/i,'');
  if(!dropoffAddress)dropoffAddress=scanText(/dropoffPlace|arrivalPlace|destinationPlace|desembarque|destino/i,'');
  add(boardingAddress); add(dropoffAddress);

  return JSON.stringify({
    specificAddresses:values.slice(0,20),
    hasSpecificAddress:values.length>0,
    boardingAddress,
    dropoffAddress,
    url:location.href||'',
    bodyText:clean(rawText).slice(0,4000)
  });
})();
