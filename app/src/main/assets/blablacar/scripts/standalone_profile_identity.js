(function() {
  const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig;
  const values = [];
  const push = (value) => {
    if (!value) return;
    const matches = String(value).match(uuid) || [];
    matches.forEach((item) => values.push(item.toLowerCase()));
  };

  push(location.href || '');
  Array.from(document.querySelectorAll('a[href]')).forEach((anchor) => {
    const href = anchor.href || '';
    if (/(?:\/user\/show\/|\/profile\/|\/member\/)/i.test(href)) push(href);
  });

  return JSON.stringify({
    documentReady: document.readyState === 'complete',
    currentUrl: location.href || '',
    profileUuids: Array.from(new Set(values)).sort()
  });
})();