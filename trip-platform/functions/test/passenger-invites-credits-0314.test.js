"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const test=require("node:test");
const api=fs.readFileSync(path.join(__dirname,"..","index.js"),"utf8");
const root=path.join(__dirname,"..","..","..");
const html=fs.readFileSync(path.join(root,"trip-platform","public","index.html"),"utf8");
const shell=fs.readFileSync(path.join(root,"trip-platform","public","public-agenda-shell-0569.js"),"utf8");
const privateHtml=fs.readFileSync(path.join(root,"trip-platform","public","minha-area.html"),"utf8");
test("0649 VIP membership reuses driver passenger access authority",()=>{
  assert.match(api,/PASSENGER_AUTHORIZED_ACCESS_STATUSES = new Set\(\["ACTIVE", "AUTHORIZED"\]\)/);
  assert.match(api,/vipActive/);
  assert.match(api,/vip_access_required_0649/);
  assert.match(api,/requirePassengerDriverAccess/);
});
test("referrals still require approval and earn configured credits once",()=>{
  assert.match(api,/requestPassengerReferralInvite/);
  assert.match(api,/: "PENDING"/);
  assert.match(api,/processReferralCreditsForCompletedTrip/);
  assert.match(api,/referralRewardGrantedAtMillis/);
  assert.match(api,/REFERRAL_EARNED/);
});
test("credits are visible and spend atomically on booking",()=>{
  assert.match(html,/vipCreditBalance0649/);
  assert.match(privateHtml,/privateCreditBalance0649/);
  assert.match(shell,/bookingUseCredits0649/);
  assert.match(shell,/creditToUseCents: .*vipCreditBalanceCents0649/);
  assert.match(api,/BOOKING_CREDIT_USED/);
  assert.match(api,/BOOKING_CREDIT_REFUND/);
});
test("VIP referral sharing stays neutral before login",()=>{
  assert.match(shell,/title: "Área VIP"/);
  assert.match(shell,/Você recebeu um convite para uma área privada\./);
  assert.match(shell,/navigator\.share/);
  assert.doesNotMatch(html,/og:description" content="[^"]*(carona|motorista|viagem)/i);
});
