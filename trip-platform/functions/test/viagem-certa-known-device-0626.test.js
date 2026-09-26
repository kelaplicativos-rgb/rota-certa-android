"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const test=require("node:test");
const root=path.join(__dirname,"..","..","..");
const api=fs.readFileSync(path.join(__dirname,"..","index.js"),"utf8");
const publicJs=fs.readFileSync(path.join(root,"trip-platform","public","public-agenda-shell-0569.js"),"utf8");
const privateJs=fs.readFileSync(path.join(root,"trip-platform","public","minha-area.js"),"utf8");
function between(source,startMarker,endMarker){const start=source.indexOf(startMarker);assert.notEqual(start,-1,startMarker+" missing");const end=source.indexOf(endMarker,start+startMarker.length);assert.notEqual(end,-1,endMarker+" missing");return source.slice(start,end);}
test("0649 uses Firebase Hosting forwarded HttpOnly session cookie",()=>{
  assert.match(api,/PASSENGER_KNOWN_DEVICE_COOKIE_0626 = "__session"/);
  assert.match(api,/Secure; HttpOnly; SameSite=Lax/);
  assert.doesNotMatch(api,/__Host-viagem_certa_device_0626/);
});
test("0649 keeps bearer until server rejects it instead of erasing it on success",()=>{
  const probe=between(publicJs,"async function probePassengerSession0626","function normalizePhoneE1640623");
  assert.match(probe,/driverUsername=/);
  assert.match(probe,/passengerAuthHeaders0626/);
  assert.doesNotMatch(probe,/if \(response\.ok\) \{\s*passengerSessionToken0623 = ""/);
  const enter=between(privateJs,"function enterPrivateMode0491","function leavePrivateMode0491");
  assert.doesNotMatch(enter,/sessionToken0491 = ""/);
});
test("0649 private refresh authenticates first then loads secondary data",()=>{
  const refresh=between(privateJs,"async function refreshPrivateArea0491","function showEntryStep0625");
  const me=refresh.indexOf('const me = await request0491("/v1/passenger/me" + scoped)');
  const enter=refresh.indexOf("enterPrivateMode0491()");
  const rest=refresh.indexOf("const [bookings, notifications, timeline] = await Promise.all");
  assert.ok(me>=0 && enter>me && rest>enter);
});
test("logout still revokes server session",()=>{
  const logout=between(api,"async function logoutPassengerAccount","async function signupPassengerAccount");
  assert.match(logout,/sessionRefId/);
  assert.match(logout,/clearPassengerKnownDeviceCookie0626\(res\)/);
  assert.match(publicJs,/fetch\("\/v1\/passenger\/logout"/);
  assert.match(privateJs,/request0491\("\/v1\/passenger\/logout"/);
});
