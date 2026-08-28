(function(){
  const clean=(v)=>(v||'').replace(/\s+/g,' ').trim();
  return JSON.stringify({url:location.href||'',fromPresent:!!document.querySelector('input'),toPresent:document.querySelectorAll('input').length>=2,searchButtonPresent:Array.from(document.querySelectorAll('button')).some((b)=>/procurar|buscar/i.test(clean(b.innerText)))});
})();
