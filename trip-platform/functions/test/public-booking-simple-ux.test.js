"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const web = fs.readFileSync(path.join(__dirname, "..", "..", "public", "app.js"), "utf8");
const html = fs.readFileSync(path.join(__dirname, "..", "..", "public", "index.html"), "utf8");

test("public booking homepage is written for a simple mobile reservation flow", () => {
  assert.match(html, /Reserve sua viagem/);
  assert.match(html, /Escolha uma viagem abaixo/);
  assert.match(web, /action\.textContent = "RESERVAR"/);
  assert.doesNotMatch(html, /Compartilhar link do calendário \(\.ics\)/);
  assert.match(html, /<summary>Mais opções<\/summary>/);
});

test("booking asks one simple question at a time with large choice controls", () => {
  assert.match(html, /Onde você vai embarcar\?/);
  assert.match(html, /Onde você vai descer\?/);
  assert.match(html, /Quantas pessoas vão viajar\?/);
  assert.match(html, /Agora seus dados/);
  assert.match(html, /class="choiceGrid"/);
  assert.match(html, /class="seatGrid"/);
  assert.match(web, /function stepTo\(id\)/);
  assert.match(web, /function choiceButton\(/);
});

test("senior-friendly flow keeps correction, double-tap protection and friendly progress", () => {
  assert.match(html, />Corrigir<\/button>/);
  assert.match(web, /confirmReserve"\)\.disabled = true/);
  assert.match(web, /CONFIRMANDO…/);
  assert.match(web, /requestIdentity\(pendingBooking\)/);
  assert.match(web, /Nenhuma reserva duplicada foi criada/);
  assert.match(web, /Confira seu WhatsApp\. Digite o DDD e o número/);
});

test("confirmation keeps technical calendar and cancellation actions secondary", () => {
  assert.match(html, /✅ RESERVA CONFIRMADA/);
  assert.match(html, /Guarde este código caso precise cancelar/);
  assert.match(html, /Precisa cancelar uma reserva\?/);
  assert.match(html, /Adicionar ao Google Agenda/);
  assert.match(html, /Baixar arquivo de calendário/);
});

test("mobile accessibility contract has large targets, labels and zoom support", () => {
  assert.match(html, /maximum-scale=5/);
  assert.match(html, /min-height:56px/);
  assert.match(html, /min-height:62px/);
  assert.match(html, /aria-live="polite"/);
  assert.match(html, /role="alert"/);
  assert.match(html, /aria-label="Embarque"/);
  assert.match(html, /aria-label="Destino"/);
});
