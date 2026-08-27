"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const api = fs.readFileSync(path.join(__dirname, "..", "index.js"), "utf8");
const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("video flow keeps large readable typography and large controls", () => {
  assert.match(html, /html\{font-size:18px\}/);
  assert.match(html, /min-height:64px/);
  assert.match(html, /font-size:21px/);
  assert.match(html, /class="stepTitle"/);
});

test("video flow has detail, request, review, message and final request stages", () => {
  assert.match(html, /Escolha sua viagem/);
  assert.match(html, /Detalhes da viagem/);
  assert.match(html, /Fazer pedido de reserva/);
  assert.match(html, /Confira os detalhes do seu pedido de reserva/);
  assert.match(html, /Resumo do preço/);
  assert.match(html, /Mande uma mensagem para o motorista/);
  assert.match(web, /showOnly\("booking"\)/);
  assert.match(web, /showOnly\("review"\)/);
  assert.match(web, /showOnly\("confirmed"\)/);
});

test("driver WhatsApp comes only from configured public driver profile", () => {
  assert.match(api, /driverWhatsapp/);
  assert.match(api, /safePublicDriverProfile/);
  assert.match(web, /driverProfile\.whatsapp/);
  assert.match(web, /https:\/\/wa\.me\//);
  assert.match(html, /driverWhatsappTrip/);
  assert.match(html, /driverWhatsappReview/);
  assert.match(html, /driverWhatsappConfirmed/);
});

test("public profile exposes video-card data only when configured", () => {
  for (const field of [
    "driverPublicAbout",
    "driverPublicRating",
    "driverPublicReviewCount",
    "driverPublicBadge",
    "vehicleMakeModel",
    "vehicleColor",
    "vehicleAmenities",
    "driverPreferences",
    "paymentInstructions",
  ]) {
    assert.match(api, new RegExp(field));
  }
  assert.match(web, /driverProfile\.rating/);
  assert.match(web, /driverProfile\.reviewCount/);
  assert.match(web, /driverProfile\.badge/);
  assert.match(web, /driverProfile\.vehicle/);
  assert.match(web, /driverProfile\.amenities/);
  assert.match(web, /driverProfile\.preferences/);
  assert.match(web, /driverProfile\.paymentInstructions/);
});

test("full trips remain unavailable and cannot enter booking flow", () => {
  assert.match(web, /isFullTrip\(trip\)/);
  assert.match(web, /show\("tripSticky", !full && trip\.canReserve !== false\)/);
  assert.match(web, /if \(!trip \|\| isFullTrip\(trip\) \|\| trip\.canReserve === false\) return/);
  assert.match(web, /action\.textContent = full \? "CHEIO" : "VER DETALHES"/);
});
