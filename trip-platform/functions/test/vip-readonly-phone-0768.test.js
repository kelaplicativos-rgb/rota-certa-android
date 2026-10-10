"use strict";
const assert=require("node:assert/strict");
const test=require("node:test");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");
const api=fs.readFileSync(path.join(__dirname,"../index.js"),"utf8");
const publicDir=path.resolve(__dirname,"../../public");
const html=fs.readFileSync(path.join(publicDir,"index.html"),"utf8");
const view=fs.readFileSync(path.join(publicDir,"vip-view-0768.js"),"utf8");
function section(from,to){const a=api.indexOf(from),b=api.indexOf(to,a+from.length);assert.ok(a>=0&&b>a,from);return api.slice(a,b);}
test("WhatsApp login is constrained to the driver allowlist and scoped read only",()=>{
  const login=section("async function loginVipPhoneView0768","async function checkVipPhoneView0768");
  assert.match(login,/enforceBookingRateLimit\(req\)/);
  assert.match(login,/normalizeBrazilWhatsapp\(/);
  assert.match(login,/resolveDriverUsername\(/);
  assert.match(login,/driverPassengerAccessRef\(username, phone\)/);
  assert.match(login,/passengerAccessIsAuthorized\(access\.data\(\)\)/);
  assert.match(login,/crypto\.randomBytes\(32\)/);
  assert.match(login,/doc\(sha256Hex\(token\)\)/);
  assert.match(login,/scope: "PUBLIC_ADS_ONLY"/);
  assert.doesNotMatch(login,/createPassengerSession\(/);
  assert.doesNotMatch(login,/passwordHash|passwordSalt|grant.*booking/i);
});
test("Read token is never accepted by booking, credits, passenger-account or mutation authorization",()=>{
  const verifier=section("async function verifyVipPhoneView0768","async function loginVipPhoneView0768");
  assert.match(section("function vipPhoneToken0768","async function verifyVipPhoneView0768"),/X-Rota-Certa-Vip-Read-Token/);
  assert.match(verifier,/expiresAtMillis/);
  assert.match(verifier,/normalizeUsername\(data\.driverUsername\) !== username/);
  assert.match(verifier,/passengerAccessIsAuthorized\(access\.data\(\)\)/);
  const agenda=section("async function getPublicDriverAgenda","async function waitPublicAgendaCanonicalChange0495");
  const changes=section("async function waitPublicAgendaCanonicalChange0495","function buildAdminHomeTrip0471");
  assert.match(agenda,/verifyVipPhoneView0768\(req, username\)/);
  assert.match(changes,/verifyVipPhoneView0768\(req, username\)/);
  const protectedSession=section("async function requirePassengerSession","function ");
  assert.doesNotMatch(protectedSession,/verifyVipPhoneView0768/);
  const router=api.slice(api.lastIndexOf("if (req.method === \"POST\" && path === \"/v1/public/vip/phone-login\""));
  assert.match(router,/phone-session/);
  assert.doesNotMatch(verifier,/\.set\(\s*\{.*passwordHash/s);
});
test("New public VIP page has just WhatsApp, no invitation workflow or password controls",()=>{
  assert.match(html,/id="passengerWhatsapp0589"/);
  assert.match(html,/id="passengerAccessContinue0589"/);
  assert.match(html,/src="\/vip-view-0768\.js\?v=1"/);
  assert.doesNotMatch(html,/id="vipPassword0649"|id="vipReferralRequest0649"|id="bookingModal0623"/);
  assert.doesNotMatch(html,/src="\/public-agenda-shell-0569\.js/);
  assert.match(html,/vipTripCard0768/);
});
test("Full card is a verified BlaBlaCar link, never an internal booking on phone-only session",()=>{
  assert.match(view,/node0768\(href\?"a":"article"/);
  assert.match(view,/card\.href=href/);
  assert.match(view,/card\.rel = "noopener noreferrer"/);
  assert.doesNotMatch(view,/openFullTripBooking0623|createBooking\(/);
  assert.match(view,/No verified fare field exists/);
  assert.match(view,/Indisponível/);
});
test("URL allowlist rejects unsafe domains, credentials and private edit routes",()=>{
  function stub(id){return {classList:{toggle(){}},textContent:"",addEventListener(){},value:"",disabled:false};}
  const c={URL,Intl,Date,Number,String,Array,Set,RegExp,sessionStorage:{getItem:()=>"",setItem(){},removeItem(){}},
    document:{getElementById:stub,createElement:()=>({}),visibilityState:"hidden"},
    location:{pathname:"/ezequiel",search:""},fetch:async()=>({ok:false,status:401}),window:{setInterval(){}},
    setInterval(){},console};
  vm.createContext(c);
  vm.runInContext(view,c);
  const validate=url=>vm.runInContext("isBlaBlaUrl0768("+JSON.stringify(url)+")",c);
  assert.match(validate("https://www.blablacar.com.br/trip/abcdef123"),/^https:\/\/www\.blablacar\.com\.br\/trip\/abcdef123/);
  for(const url of ["javascript:alert(1)","https://blablacar.com.br.evil.net/trip/abcdef123",
    "https://www.blablacar.com.br/rides/offer/edit/abcdef123",
    "https://evil.com/trip/abcdef123","http://www.blablacar.com.br/trip/abcdef123",
    "https://user:password@www.blablacar.com.br/trip/abcdef123"])assert.equal(validate(url),"",url);
});
