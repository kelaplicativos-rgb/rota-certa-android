"use strict";

const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..", "..", "..");
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), "utf8");
const write = (parts, value) => fs.writeFileSync(path.join(root, ...parts), value);

function replaceOnce(source, before, after, label) {
  const count = source.split(before).length - 1;
  if (count !== 1) throw new Error(`${label}: expected one anchor, found ${count}`);
  return source.replace(before, after);
}

function replaceRegexOnce(source, regex, after, label) {
  const matches = source.match(regex);
  if (!matches || matches.length !== 1) throw new Error(`${label}: expected one regex match, found ${matches ? matches.length : 0}`);
  return source.replace(regex, after);
}

// 1) Backend: public Agenda only promotes share-compatible BlaBlaCar links.
{
  const parts = ["trip-platform", "functions", "index.js"];
  let source = read(...parts);
  if (!source.includes("function normalizeBlaBlaPublicShareUrl0571(")) {
    const marker = "async function publicAgendaExactBlaBlaLinks0570(driver, sourceDocs, rawTrips) {";
    const helper = `function normalizeBlaBlaPublicShareUrl0571(raw) {\n  const value = cleanText(raw, 1200);\n  if (!value) return \"\";\n  try {\n    const url = new URL(value);\n    if (![\"http:\", \"https:\"].includes(url.protocol) || !isOfficialBlaBlaHost(url.hostname)) return \"\";\n    if (url.username || url.password || (url.port && ![\"80\", \"443\"].includes(url.port))) return \"\";\n    const path = url.pathname.replace(/\\/+$/, \"\").toLowerCase();\n    if (path !== \"/trip\" && !path.startsWith(\"/trip/\")) return \"\";\n    const forbidden = new Set([\"requested_seats\", \"search_origin\", \"search_uuid\"]);\n    for (const key of url.searchParams.keys()) {\n      if (forbidden.has(String(key).toLowerCase())) return \"\";\n    }\n    const publicId = blaBlaExternalTripId(url);\n    if (!publicId || !/^[A-Za-z0-9_-]{6,}$/.test(publicId)) return \"\";\n    const sourceParam = cleanText(url.searchParams.get(\"source\"), 40).toUpperCase();\n    if (sourceParam && sourceParam !== \"CARPOOLING\") return \"\";\n    url.protocol = \"https:\";\n    if (url.port === \"80\" || url.port === \"443\") url.port = \"\";\n    url.hash = \"\";\n    return url.toString();\n  } catch (_) {\n    return \"\";\n  }\n}\n\n`;
    const at = source.indexOf(marker);
    if (at < 0) throw new Error("backend: publicAgendaExactBlaBlaLinks0570 anchor missing");
    source = source.slice(0, at) + helper + source.slice(at);
  }
  source = replaceOnce(
    source,
    "    return exactPublicUrl ? { ...trip, blablaPublicUrl: exactPublicUrl } : trip;",
    "    const strictPublicShareUrl0571 = normalizeBlaBlaPublicShareUrl0571(exactPublicUrl);\n    return { ...trip, blablaPublicUrl: strictPublicShareUrl0571 };",
    "backend strict projection",
  );
  write(parts, source);
}

// 2) Public shell: fail closed on search-context links and normalize official http shares to https.
{
  const parts = ["trip-platform", "public", "public-agenda-shell-0569.js"];
  let source = read(...parts);
  const regex = /function validatedBlaBlaPublicUrl0569\(raw\) \{[\s\S]*?\n\}\n\nfunction whatsappDigits0569/;
  const replacement = `function validatedBlaBlaPublicUrl0569(raw) {\n  const value = String(raw || \"\").trim().slice(0, 1200);\n  if (!value) return \"\";\n  try {\n    const url = new URL(value);\n    if (![\"http:\", \"https:\"].includes(url.protocol) || !isOfficialBlaBlaHost0569(url.hostname)) return \"\";\n    if (url.username || url.password || (url.port && ![\"80\", \"443\"].includes(url.port))) return \"\";\n    const normalizedPath = url.pathname.replace(/\\/+$/, \"\").toLowerCase();\n    let publicId = \"\";\n    if (normalizedPath === \"/trip\") {\n      publicId = String(url.searchParams.get(\"id\") || \"\").trim();\n    } else if (normalizedPath.startsWith(\"/trip/\")) {\n      const match = url.pathname.match(/\\/trip\\/([^/?#]+)/i);\n      publicId = match ? String(match[1] || \"\").trim() : \"\";\n    } else {\n      return \"\";\n    }\n    if (!/^[A-Za-z0-9_-]{6,}$/.test(publicId)) return \"\";\n    const forbidden = new Set([\"requested_seats\", \"search_origin\", \"search_uuid\"]);\n    for (const key of url.searchParams.keys()) {\n      if (forbidden.has(String(key).toLowerCase())) return \"\";\n    }\n    const sourceParam = String(url.searchParams.get(\"source\") || \"\").trim().toUpperCase();\n    if (sourceParam && sourceParam !== \"CARPOOLING\") return \"\";\n    url.protocol = \"https:\";\n    if (url.port === \"80\" || url.port === \"443\") url.port = \"\";\n    url.hash = \"\";\n    return url.toString();\n  } catch (_) {\n    return \"\";\n  }\n}\n\nfunction whatsappDigits0569`;
  source = replaceRegexOnce(source, regex, replacement, "public shell validator");
  write(parts, source);
}

// 3) Android URL authority: search URLs can never become passenger-facing public share URLs.
{
  const parts = ["app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaCollectorUrlModule.kt"];
  let source = read(...parts);
  if (!source.includes("private fun isShareCompatiblePublicTrip0571(")) {
    const marker = "    /** Passenger-facing exact trip URL. Administrative /rides/offer URLs are rejected. */";
    const helper = `    private fun isShareCompatiblePublicTrip0571(uri: URI): Boolean {\n        val forbidden = setOf(\"requested_seats\", \"search_origin\", \"search_uuid\")\n        val hasForbidden = uri.rawQuery.orEmpty()\n            .split('&')\n            .filter(String::isNotBlank)\n            .map { part -> part.substringBefore('=', missingDelimiterValue = \"\").lowercase() }\n            .any(forbidden::contains)\n        if (hasForbidden) return false\n        val source = queryValue(uri, \"source\")?.trim().orEmpty()\n        return source.isBlank() || source.equals(\"CARPOOLING\", ignoreCase = true)\n    }\n\n`;
    source = replaceOnce(source, marker, helper + marker, "android share helper");
  }
  source = replaceOnce(
    source,
    "        if (path != \"/trip\" && !path.startsWith(\"/trip/\")) return null\n        val actualTripId = tripId(value)",
    "        if (path != \"/trip\" && !path.startsWith(\"/trip/\")) return null\n        if (!isShareCompatiblePublicTrip0571(uri)) return null\n        val actualTripId = tripId(value)",
    "android publicTrip guard",
  );
  source = replaceOnce(
    source,
    "        if (path != \"/trip\" && !path.startsWith(\"/trip/\")) return null\n        val query = uri.rawQuery.orEmpty()",
    "        if (path != \"/trip\" && !path.startsWith(\"/trip/\")) return null\n        if (!isShareCompatiblePublicTrip0571(uri)) return null\n        val query = uri.rawQuery.orEmpty()",
    "android authoritative guard",
  );
  source = source.replace(
    ".filterNot { part -> part.substringBefore('=').equals(\"search_uuid\", ignoreCase = true) }\n            .joinToString(\"&\")\n        val promoted = buildString {",
    ".joinToString(\"&\")\n        val promoted = buildString {",
  );
  write(parts, source);
}

// 4) TRIP_PUBLIC_SHARE: do not let passive search URLs short-circuit navigator.share capture.
{
  const parts = ["app", "src", "main", "assets", "blablacar", "scripts", "trip_public_share.js"];
  let source = read(...parts);
  const urlRegex = /  const publicTripUrl = \(raw, requireAdministrativeId\) => \{[\s\S]*?\n  \};\n\n  const exactPublicTripUrl/;
  const urlReplacement = `  const publicTripUrl = (raw, requireAdministrativeId) => {\n    if (!tripId) return '';\n    try {\n      const url = new URL(raw || '', location.href);\n      if (!['http:', 'https:'].includes(url.protocol) || !isOfficialBlaBlaHost(url.hostname)) return '';\n      if (url.username || url.password || (url.port && !['80', '443'].includes(url.port))) return '';\n      const path = url.pathname.replace(/\\/+$/, '').toLowerCase();\n      if (path !== '/trip' && !path.startsWith('/trip/')) return '';\n      if (['requested_seats', 'search_origin', 'search_uuid'].some((key) => url.searchParams.has(key))) return '';\n      const sourceParam = clean(url.searchParams.get('source')).toUpperCase();\n      if (sourceParam && sourceParam !== 'CARPOOLING') return '';\n      let id = clean(url.searchParams.get('id'));\n      if (!id) {\n        const match = url.pathname.match(/\\/trip\\/([^/?#]+)/i);\n        id = clean(match && match[1]);\n      }\n      if (!/^[A-Za-z0-9_-]{6,}$/.test(id)) return '';\n      if (requireAdministrativeId && id !== tripId) return '';\n      url.protocol = 'https:';\n      if (url.port === '80' || url.port === '443') url.port = '';\n      url.hash = '';\n      return url.href;\n    } catch (_) {\n      return '';\n    }\n  };\n\n  const exactPublicTripUrl`;
  source = replaceRegexOnce(source, urlRegex, urlReplacement, "share script URL validator");

  const passiveRegex = /\n  if \(!state\.publicTripHref\) \{\n    Array\.from\(document\.querySelectorAll\([\s\S]*?\n  \}\n\n  const capturePayload/;
  source = replaceRegexOnce(
    source,
    passiveRegex,
    "\n  // 0.1.569/0571: generic page links are not public-share authority.\n  // A public token may differ from the administrative trip id, so capture the\n  // actual share payload instead of accepting a search/navigation URL.\n  if (state.publicTripHref && !authoritativeSharedPublicTripUrl(state.publicTripHref)) {\n    state.publicTripHref = '';\n  }\n\n  const capturePayload",
    "remove passive public-link shortcut",
  );
  source = replaceOnce(
    source,
    "      return candidates.some(acceptCandidate);",
    "      return candidates.some((candidate) => acceptCandidate(candidate, true));",
    "share control authoritative candidate",
  );
  write(parts, source);
}

// 5) Public-shell tests must assert fail-closed search context.
{
  const parts = ["trip-platform", "functions", "test", "public-agenda-shell-0569.test.js"];
  let source = read(...parts);
  source = source.replace(
    "  assert.match(app, /url\\.protocol !== \"https:\"/);",
    "  assert.match(app, /\\[\"http:\", \"https:\"\\]\\.includes\\(url\\.protocol\\)/);\n  assert.match(app, /requested_seats/);\n  assert.match(app, /search_origin/);\n  assert.match(app, /sourceParam.*CARPOOLING/);",
  );
  write(parts, source);
}

// 6) Release identity for the Android collector fix.
{
  const parts = ["app", "build.gradle.kts"];
  let source = read(...parts);
  source = source.replace("val releaseVersionCode = 5_859", "val releaseVersionCode = 5_860");
  source = source.replace('val releaseVersionName = "0.1.568"', 'val releaseVersionName = "0.1.569"');
  write(parts, source);

  const historyParts = ["app", "src", "main", "assets", "release_history.json"];
  const history = JSON.parse(read(...historyParts));
  history.releases = Array.isArray(history.releases) ? history.releases : [];
  if (!history.releases.some((entry) => entry && entry.version === "0.1.569" && Number(entry.build) === 5860)) {
    history.releases.unshift({
      version: "0.1.569",
      build: 5860,
      commit: null,
      branch: "agent/public-agenda-shell-blablacar-whatsapp-0.1.569",
      generatedAt: null,
      status: "EM_VALIDACAO",
      implemented: [
        "TRIP_PUBLIC_SHARE passa a usar o payload real de compartilhamento da BlaBlaCar como autoridade do permalink público, inclusive quando o token público difere do identificador administrativo da viagem."
      ],
      fixed: [
        "URLs de contexto de busca com requested_seats, search_origin ou search_uuid deixam de ser promovidas a blablaPublicUrl na captura Android, no backend e na Agenda Pública.",
        "A captura deixa de aceitar um link /trip passivo da página antes de acionar Compartilhar esta carona, evitando que um ID administrativo bloqueie a obtenção do token público real."
      ],
      improved: [
        "Links oficiais de compartilhamento em http são promovidos para https sem alterar source=CARPOOLING, o id público nem parâmetros legítimos como p0[ac]=adult.",
        "Sem permalink público comprovado, o card público permanece visível porém inerte, sem reconstruir ou inferir um link a partir do tripId administrativo."
      ],
      regressions: [],
      resolvedRegressions: [
        "0.1.570: a prova ao vivo validava apenas host, caminho e id e podia classificar um URL de busca como permalink público válido."
      ],
      modulesAffected: ["Agenda Pública", "BlaBlaCar", "Coletor", "Links públicos", "Build"],
      detailsComplete: true
    });
  }
  write(historyParts, JSON.stringify(history, null, 2) + "\n");
}

const checks = [
  [read("trip-platform", "functions", "index.js").includes("normalizeBlaBlaPublicShareUrl0571"), "backend helper"],
  [read("trip-platform", "public", "public-agenda-shell-0569.js").includes("requested_seats"), "shell forbidden params"],
  [read("app", "src", "main", "java", "br", "com", "mapeiaia", "rotacerta", "trips", "BlaBlaCollectorUrlModule.kt").includes("isShareCompatiblePublicTrip0571"), "android URL authority"],
  [!read("app", "src", "main", "assets", "blablacar", "scripts", "trip_public_share.js").includes("link[rel=\"canonical\"]"), "passive shortcut removed"],
  [read("app", "build.gradle.kts").includes('releaseVersionName = "0.1.569"'), "version bump"],
];
for (const [ok, label] of checks) if (!ok) throw new Error(`0571 verification failed: ${label}`);
console.log("PUBLIC_AGENDA_SHARE_AUTHORITY_0571_PATCH=PASS");
