"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { createRemoteHealth0747 } = require("../remote-health-0747");

function harness({mode="HEALTH_SNAPSHOT",state="PENDING_DEVICE",expiresAtMillis=Date.now()+60000,allow=true}={}) {
  let reads=0;
  const uuid="123e4567-e89b-42d3-a456-426614174000";
  const db={collection(name){return {doc(){return {async get(){
    reads++;
    if(name==="tripRemoteHealthState0747")return {data:()=>({latestHealthJobId:uuid})};
    if(name==="tripRemoteHealthJobs0747")return {exists:true,data:()=>({mode,state,expiresAtMillis,requestedAtMillis:Date.now()-1000})};
    throw Error("Unexpected db collection");
  }}}}}};
  const api=createRemoteHealth0747({db,requireDriver:async()=>allow?{username:"driver"}:null,
    getMessaging:()=>{throw Error("No push required")},normalizeUsername:s=>s,
    json:(r,status,body)=>{r.body=body;r.status=status;return body},
    fail:()=>{throw Error("Unexpected HTTP failure")}
  });
  return {api,res:{set:()=>{}},reads:()=>reads};
}
test("authenticated polling returns only health snapshot job metadata",async()=>{
  const h=harness();
  await h.api.pendingHealthDriver0762({},h.res);
  assert.equal(h.res.status,200);
  assert.equal(h.res.body.pending,true);
  assert.equal(h.res.body.mode,"HEALTH_SNAPSHOT");
  assert.equal(h.reads(),2);
});
test("running, expired, ZIP, or unauthorized jobs cannot trigger health collection",async()=>{
  for(const config of [{state:"RUNNING"},{mode:"TECHNICAL_ZIP"},{expiresAtMillis:Date.now()-1000}]) {
    const h=harness(config);
    await h.api.pendingHealthDriver0762({},h.res);
    assert.equal(h.res.body.pending,false);
    assert.equal(h.res.body.jobId,"");
  }
  const h=harness({allow:false});
  await h.api.pendingHealthDriver0762({},h.res);
  assert.equal(h.reads(),0);
});
test("route, Android polling and explicit consent remain connected",()=>{
  const index=fs.readFileSync(path.join(__dirname,"../index.js"),"utf8");
  const app=fs.readFileSync(path.join(__dirname,"../../../app/src/main/java/br/com/mapeiaia/rotacerta/trips/RemoteCoversPollService0758.kt"),"utf8");
  assert.match(index,/remote-health\/pending-snapshot/);
  assert.match(index,/pendingHealthDriver0762/);
  assert.match(app,/pollRemoteHealthPending0762/);
  assert.match(app,/showConsent\("remote_health_collect", jobId\)/);
  assert.doesNotMatch(app,/RemoteHealthScheduler0747\.enqueue/);
});
