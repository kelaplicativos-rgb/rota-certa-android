"use strict";

(() => {
  const RECAPTCHA_ENTERPRISE_SITE_KEY = "6LdqZpotAAAAANdCM4Uj7UWOi6XSLSUfL_1clvMb";
  let bootstrapPromise = null;

  function bootstrap() {
    if (bootstrapPromise) return bootstrapPromise;
    bootstrapPromise = (async () => {
      if (!window.firebase || !firebase.appCheck || !firebase.auth) {
        throw new Error("Firebase security SDK unavailable");
      }
      const appCheck = firebase.appCheck();
      appCheck.activate(
        new firebase.appCheck.ReCaptchaEnterpriseProvider(RECAPTCHA_ENTERPRISE_SITE_KEY),
        true,
      );
      const auth = firebase.auth();
      if (!auth.currentUser) {
        await auth.signInAnonymously();
      }
      if (!auth.currentUser) throw new Error("Anonymous Firebase session unavailable");
      return { appCheck, auth };
    })();
    return bootstrapPromise;
  }

  async function protectedHeaders() {
    const { appCheck, auth } = await bootstrap();
    const user = auth.currentUser;
    if (!user) throw new Error("Anonymous Firebase session unavailable");
    const [idToken, appCheckResult] = await Promise.all([
      user.getIdToken(),
      appCheck.getToken(false),
    ]);
    if (!appCheckResult || !appCheckResult.token) {
      throw new Error("Firebase App Check token unavailable");
    }
    return {
      Authorization: `Bearer ${idToken}`,
      "X-Firebase-AppCheck": appCheckResult.token,
    };
  }

  async function fetchProtected(input, init = {}) {
    const securityHeaders = await protectedHeaders();
    const headers = new Headers(init.headers || {});
    Object.entries(securityHeaders).forEach(([name, value]) => headers.set(name, value));
    return fetch(input, { ...init, headers });
  }

  window.RotaCertaFirebaseSecurity = { bootstrap, protectedHeaders, fetchProtected };
  bootstrap().catch(() => {
    // Public trip browsing remains available; protected mutations will show a
    // friendly validation error if the browser cannot obtain Firebase tokens.
  });
})();
