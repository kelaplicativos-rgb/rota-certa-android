(function(){
  const root=document.scrollingElement||document.documentElement;
  const beforeY=Math.round(window.scrollY||root?.scrollTop||0);
  const scrollHeight=Math.max(root?.scrollHeight||0,document.body?.scrollHeight||0);
  window.scrollTo(0,scrollHeight);
  return JSON.stringify({beforeY,afterY:Math.round(window.scrollY||root?.scrollTop||0),scrollHeight});
})();