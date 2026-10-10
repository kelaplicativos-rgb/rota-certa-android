"use strict";

// Dedicated read-only VIP visitor application. It deliberately does not import
// booking, credits, password or account-management scripts.
const $vip0768 = (id) => document.getElementById(id);
const slug0768 = (() => {
  const path = location.pathname.split("/").filter(Boolean);
  if (path.length !== 1) return "";
  try { return decodeURIComponent(path[0]).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0,32); }
  catch (_) { return ""; }
})();
const driver0768 = slug0768 || new URLSearchParams(location.search).get("motorista") || "";
const sessionKey0768 = "vipPhoneView0768:" + driver0768;
let token0768 = sessionStorage.getItem(sessionKey0768) || "";
let active0768 = false;
let loading0768 = false;
const visibleStatuses0768 = new Set(["PUBLISHED","FULL","STARTING","ACTIVE"]);

function normalizePhone0768(raw) {
  const value = String(raw || "").trim();
  const digits = value.replace(/\D/g, "");
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) return "+" + digits;
  if (digits.length === 10 || digits.length === 11) return "+55" + digits;
  throw new Error("Informe seu WhatsApp com DDD.");
}
function displayError0768(message) {
  const area = $vip0768("passengerAccessMessage0589");
  if (area) { area.textContent = message || ""; area.classList.toggle("hidden", !message); }
}
function showArea0768(on) {
  active0768 = on;
  $vip0768("accessGate0589")?.classList.toggle("hidden", on);
  $vip0768("agenda")?.classList.toggle("hidden", !on);
  $vip0768("passengerNav0589")?.classList.toggle("hidden", !on);
  $vip0768("passengerAgendaLogout0589")?.classList.toggle("hidden", !on);
  $vip0768("vipBrand0649").textContent = on ? "VIAGEM CERTA" : "♛";
  $vip0768("vipTitle0649").textContent = on ? "Viagens exclusivas" : "Acesso exclusivo";
}
function isBlaBlaUrl0768(raw) {
  try {
    const u = new URL(String(raw || ""));
    if (u.protocol !== "https:" || u.username || u.password || u.port) return "";
    if (!/^(www\.)?blablacar\.(com|[a-z]{2}|com\.[a-z]{2})$/i.test(u.hostname)) return "";
    const id = u.pathname.match(/^\/trip\/([a-z0-9_-]{6,})\/?$/i)?.[1] ||
      (u.pathname.replace(/\/$/, "") === "/trip" ? u.searchParams.get("id") : "");
    if (!id || !/^[A-Za-z0-9_-]{6,}$/.test(id)) return "";
    u.hash = "";
    for (const key of ["search_uuid","requested_seats","search_origin"]) u.searchParams.delete(key);
    return u.toString();
  } catch (_) { return ""; }
}
function time0768(value, zone) {
  if (!Number.isFinite(Number(value)) || Number(value) < 1) return "—";
  try {return new Intl.DateTimeFormat("pt-BR", {hour:"2-digit",minute:"2-digit",hour12:false,timeZone: zone || "America/Sao_Paulo"}).format(Number(value));}
  catch (_) {return "—";}
}
function date0768(value, zone) {
  if (!Number.isFinite(Number(value)) || Number(value) < 1) return {key:"",label:"Data não informada"};
  const d = new Date(Number(value));
  const opts = {year:"numeric",month:"2-digit",day:"2-digit",timeZone:zone || "America/Sao_Paulo"};
  const parts = new Intl.DateTimeFormat("en-CA",opts).formatToParts(d);
  const p = Object.fromEntries(parts.filter(x=>x.type !== "literal").map(x=>[x.type,x.value]));
  const key = p.year+"-"+p.month+"-"+p.day;
  const label = new Intl.DateTimeFormat("pt-BR",{weekday:"long",day:"2-digit",month:"long",timeZone:zone || "America/Sao_Paulo"}).format(d);
  return {key,label:label.charAt(0).toUpperCase()+label.slice(1)};
}
function node0768(tag,className,content) {
  const el=document.createElement(tag);
  if(className) el.className=className;
  if(content!==undefined) el.textContent=String(content);
  return el;
}
function renderCard0768(trip) {
  const stops=[...(Array.isArray(trip.stops)?trip.stops:[])].sort((a,b)=>Number(a.order||0)-Number(b.order||0));
  if(stops.length<2) return null;
  const from=String(stops[0].name||"Origem");
  const to=String(stops[stops.length-1].name||"Destino");
  const href=isBlaBlaUrl0768(trip.blablaPublicUrl);
  const card=node0768(href?"a":"article","vipTripCard0768"+(href?" vipTripLinked0768":" vipTripUnavailable0768"));
  if(href){card.href=href; card.rel="noopener noreferrer";card.setAttribute("aria-label","Abrir anúncio público BlaBlaCar: "+from+" para "+to);}
  const start=Number(trip.departureAtMillis||stops[0].plannedDepartureMillis||stops[0].plannedArrivalMillis||0);
  const end=Number(stops[stops.length-1].plannedArrivalMillis||stops[stops.length-1].plannedDepartureMillis||0);
  const duration=start>0 && end>start ? Math.round((end-start)/60000):0;
  const tripMain=node0768("div","vipTripMain0768");
  const clock=node0768("div","vipTripTimes0768");
  clock.append(node0768("strong","",time0768(start,trip.timezoneId)));
  if(duration>0)clock.append(node0768("small","",Math.floor(duration/60)+"h"+String(duration%60).padStart(2,"0")));
  clock.append(node0768("strong","",time0768(end,trip.timezoneId)));
  const rail=node0768("span","vipTripRail0768");rail.setAttribute("aria-hidden","true");
  const cities=node0768("div","vipTripCities0768");
  cities.append(node0768("strong","",from),node0768("strong","",to));
  const status=String(trip.status||"").toUpperCase();
  const full=status==="FULL"||trip.isFull===true;
  // No verified fare field exists in the public Agenda payload; do not invent or
  // extrapolate ticket price. Fare can be seen in the actual BlaBlaCar advert.
  const fare=node0768("strong","vipTripFare0768",full?"Cheio":href?"Ver preço":"Indisponível");
  tripMain.append(clock,rail,cities,fare);
  card.append(tripMain);
  const footer=node0768("div","vipTripFooter0768");
  footer.append(node0768("span","","🚘"),node0768("span","vipTripDriver0768",trip.blablaProfileName||trip.driverDisplayName||"Motorista"));
  if(!href)footer.append(node0768("small","vipTripUnavailableText0768","Anúncio público indisponível"));
  card.append(footer);
  return card;
}
function render0768(trips) {
  const container=$vip0768("agendaTrips"); if(!container)return;
  container.replaceChildren();
  const visible=(Array.isArray(trips)?trips:[]).filter(x=>visibleStatuses0768.has(String(x?.status||"").toUpperCase()))
    .sort((a,b)=>Number(a.departureAtMillis||0)-Number(b.departureAtMillis||0));
  const today=date0768(Date.now(),"America/Sao_Paulo").key;
  let last="";
  visible.forEach(trip=>{
    const when=date0768(trip.departureAtMillis,trip.timezoneId);
    const card=renderCard0768(trip);
    if(!card)return;
    if(when.key!==last){container.append(node0768("h2","vipDayHeading0768",when.key===today?"Hoje":when.label));last=when.key;}
    container.append(card);
  });
  if(!container.children.length)container.append(node0768("p","vipEmpty0768","Nenhuma viagem publicada no momento."));
}
async function load0768() {
  if(!active0768||loading0768)return;
  loading0768=true;
  try{
    const response=await fetch("/v1/public/agenda/"+encodeURIComponent(driver0768),{
      headers:{"Accept":"application/json","X-Rota-Certa-Vip-Read-Token":token0768},cache:"no-store"});
    if(response.status===401||response.status===403){
      token0768=""; sessionStorage.removeItem(sessionKey0768);showArea0768(false);
      displayError0768("Seu acesso não está autorizado. Informe o WhatsApp novamente.");return;
    }
    if(!response.ok)throw new Error("Não foi possível carregar as viagens.");
    const body=await response.json();render0768(body.trips);
  }catch(e){const el=$vip0768("agendaTrips");if(el)el.textContent=e.message||"Falha temporária ao carregar.";}
  finally{loading0768=false;}
}
async function login0768(){
  const button=$vip0768("passengerAccessContinue0589"); if(button?.disabled)return;
  if(button)button.disabled=true;
  displayError0768("");
  try{
    const passengerContact=normalizePhone0768($vip0768("passengerWhatsapp0589").value);
    const response=await fetch("/v1/public/vip/phone-login",{
      method:"POST",headers:{"Accept":"application/json","Content-Type":"application/json"},
      cache:"no-store",body:JSON.stringify({passengerContact,publicSlug:driver0768})});
    if(!response.ok)throw new Error(response.status===403
      ?"Este WhatsApp não está autorizado para acessar esta área.":"Não foi possível validar seu acesso.");
    const data=await response.json();
    if(data.access!=="PUBLIC_ADS_ONLY"||!/^[a-zA-Z0-9_-]{40,200}$/.test(data.sessionToken||""))throw new Error("Acesso indisponível.");
    token0768=data.sessionToken;sessionStorage.setItem(sessionKey0768,token0768);
    showArea0768(true);await load0768();
  }catch(e){displayError0768(e.message||"Não foi possível entrar.");}
  finally{if(button)button.disabled=false;}
}
async function boot0768(){
  $vip0768("passengerAccessContinue0589")?.addEventListener("click",login0768);
  $vip0768("passengerWhatsapp0589")?.addEventListener("keydown",e=>{if(e.key==="Enter")void login0768();});
  $vip0768("passengerAgendaLogout0589")?.addEventListener("click",()=>{
    token0768="";sessionStorage.removeItem(sessionKey0768);showArea0768(false);displayError0768("");
  });
  showArea0768(false);
  if(!token0768)return;
  try{
    const response=await fetch("/v1/public/vip/phone-session?driverUsername="+encodeURIComponent(driver0768),
      {headers:{"X-Rota-Certa-Vip-Read-Token":token0768},cache:"no-store"});
    if(!response.ok)throw Error("expired");
    showArea0768(true);await load0768();
  }catch(_){token0768="";sessionStorage.removeItem(sessionKey0768);}
}
void boot0768();
window.setInterval(()=>{if(active0768&&document.visibilityState==="visible")void load0768();},30000);
