(function() {
  const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
  const marker = (node) => clean(
    ((node && node.getAttribute && node.getAttribute('data-testid')) || '') + ' ' +
    ((node && node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
    ((node && node.getAttribute && node.getAttribute('title')) || '') + ' ' +
    ((node && node.innerText) || '')
  ).toLowerCase();
  const buttons = Array.from(document.querySelectorAll('button, [role="button"]'));
  let remove = buttons.find((node) => /decrement|decrease|remove|minus/.test(marker(node)) || /^[−–-]$/.test(clean(node.innerText)));
  let add = buttons.find((node) => /increment|increase|add|plus/.test(marker(node)) || /^\+$/.test(clean(node.innerText)));
  let root = (remove && remove.parentElement) || (add && add.parentElement) || null;
  while (root && root !== document.body && root.querySelectorAll('button, [role="button"]').length < 2) root = root.parentElement;
  const groupedButtons = root ? Array.from(root.querySelectorAll('button, [role="button"]')) : [];
  if (!remove && groupedButtons.length >= 2) remove = groupedButtons[0];
  if (!add && groupedButtons.length >= 2) add = groupedButtons[groupedButtons.length - 1];
  root = root || document.querySelector('[data-testid*="seat"], [data-testid*="capacity"], [role="spinbutton"]') || document.body;
  const numericControl = root.querySelector('input[type="number"], [role="spinbutton"], select');
  const controlledValue = numericControl && clean(
    numericControl.value || numericControl.getAttribute('aria-valuenow') || numericControl.getAttribute('value') || ''
  );
  const leaves = Array.from(root.querySelectorAll('span, p, div'))
    .filter((node) => node.children.length === 0)
    .map((node) => clean(node.innerText))
    .filter((text) => /^\d{1,3}$/.test(text));
  let seats = /^\d{1,3}$/.test(controlledValue || '') ? parseInt(controlledValue, 10) : (leaves.length ? parseInt(leaves[0], 10) : -1);
  if (seats < 0) {
    const all = clean(root.innerText).match(/(?:^|\s)(\d{1,3})(?:\s|$)/);
    seats = all ? parseInt(all[1], 10) : -1;
  }
  const save = document.querySelector('button[type="submit"], [data-testid*="save"], [data-testid*="submit"]');
  const clone = document.documentElement.cloneNode(true);
  clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
  clone.querySelectorAll('input, textarea').forEach((node) => { node.removeAttribute('value'); node.textContent = ''; });
  const html = clone.outerHTML || '';
  return JSON.stringify({
    seats: Number.isFinite(seats) ? seats : -1,
    canAdd: !!add && !add.disabled,
    canRemove: !!remove && !remove.disabled,
    savePresent: !!save,
    pageUrl: location.href || '',
    domHtml: html.slice(0, 350000)
  });
})();
