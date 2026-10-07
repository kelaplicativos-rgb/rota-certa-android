(() => {
  "use strict";

  const byId = (id) => document.getElementById(id);
  let newPasswordFlow = false;
  let confirmationStep = false;

  function setGateMessage(message) {
    const node = byId("passengerAccessMessage0589");
    if (!node) return;
    node.textContent = String(message || "");
    node.classList.toggle("hidden", !message);
    if (message) node.style.color = "#8f1d1d";
  }

  function ensureBackButton() {
    let back = byId("vipPasswordBack20261007");
    if (back) return back;
    const primary = byId("passengerAccessContinue0589");
    if (!primary) return null;
    back = document.createElement("button");
    back.id = "vipPasswordBack20261007";
    back.className = "accessSecondary0651 hidden";
    back.type = "button";
    back.textContent = "Voltar";
    primary.insertAdjacentElement("afterend", back);
    back.addEventListener("click", () => {
      if (!newPasswordFlow || !confirmationStep) return;
      confirmationStep = false;
      const passwordWrap = byId("vipPasswordWrap0649");
      const confirmWrap = byId("vipPasswordConfirmWrap0649");
      const confirmInput = byId("vipPasswordConfirm0649");
      if (passwordWrap) passwordWrap.classList.remove("hidden");
      if (confirmWrap) confirmWrap.classList.add("hidden");
      if (confirmInput) confirmInput.value = "";
      back.classList.add("hidden");
      const primaryButton = byId("passengerAccessContinue0589");
      if (primaryButton) primaryButton.textContent = "PRÓXIMA ETAPA";
      setGateMessage("");
      window.setTimeout(() => byId("vipPassword0649")?.focus(), 30);
    });
    return back;
  }

  function detectNewPasswordFlow() {
    const gate = byId("accessGate0589");
    const passwordWrap = byId("vipPasswordWrap0649");
    const confirmWrap = byId("vipPasswordConfirmWrap0649");
    const forgot = byId("vipForgotPassword0651");
    const primary = byId("passengerAccessContinue0589");
    const back = ensureBackButton();
    if (!gate || !passwordWrap || !confirmWrap || !forgot || !primary || !back) return;

    if (gate.classList.contains("hidden")) {
      newPasswordFlow = false;
      confirmationStep = false;
      back.classList.add("hidden");
      return;
    }

    const passwordVisible = !passwordWrap.classList.contains("hidden");
    const confirmVisible = !confirmWrap.classList.contains("hidden");
    const forgotHidden = forgot.classList.contains("hidden");

    if (!newPasswordFlow && passwordVisible && confirmVisible && forgotHidden) {
      newPasswordFlow = true;
      confirmationStep = false;
      confirmWrap.classList.add("hidden");
      back.classList.add("hidden");
      primary.textContent = "PRÓXIMA ETAPA";
    }
  }

  function goToConfirmation(event) {
    if (!newPasswordFlow || confirmationStep) return false;
    const password = String(byId("vipPassword0649")?.value || "").trim();
    event.preventDefault();
    event.stopImmediatePropagation();
    if (!/^\d{4}$/.test(password)) {
      setGateMessage("Sua senha precisa ter exatamente 4 números.");
      return true;
    }

    confirmationStep = true;
    const passwordWrap = byId("vipPasswordWrap0649");
    const confirmWrap = byId("vipPasswordConfirmWrap0649");
    const primary = byId("passengerAccessContinue0589");
    const back = ensureBackButton();
    if (passwordWrap) passwordWrap.classList.add("hidden");
    if (confirmWrap) confirmWrap.classList.remove("hidden");
    if (primary) primary.textContent = "CRIAR SENHA E ENTRAR";
    if (back) back.classList.remove("hidden");
    setGateMessage("");
    window.setTimeout(() => byId("vipPasswordConfirm0649")?.focus(), 30);
    return true;
  }

  document.addEventListener("click", (event) => {
    if (event.target === byId("passengerAccessContinue0589")) goToConfirmation(event);
  }, true);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && event.target === byId("vipPassword0649")) {
      goToConfirmation(event);
    }
  }, true);

  const observer = new MutationObserver(detectNewPasswordFlow);
  observer.observe(document.documentElement, {
    subtree: true,
    attributes: true,
    attributeFilter: ["class"],
    childList: true,
  });

  detectNewPasswordFlow();
})();