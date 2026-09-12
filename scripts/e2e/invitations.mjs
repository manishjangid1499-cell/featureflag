import { randomBytes } from 'node:crypto';
import { base, mailApi, settings, results, insist, record, processRun, ownedContainer, decodeMail,
  waitUntil, api, login, browserSession } from './helpers.mjs';

let browser;
const startedAt = new Date().toISOString();
const sensitive = [];
const suffix = randomBytes(5).toString('hex');
const recipient = 'invitation-' + suffix + '@example.test';
const password = randomBytes(20).toString('base64url');
sensitive.push(password);
async function messageFor(email, count = 1) {
  return await waitUntil(async () => {
    const messages = await (await fetch(mailApi + '/messages')).json();
    const matching = messages.map(decodeMail)
      .filter(value => value.includes(email));
    if (matching.length < count) return;
    const mail = matching.at(-1);
    const token = mail.match(/accept-invitation\?token=([A-Za-z0-9_-]+)/)?.[1];
    insist(token, 'Invitation link absent from test email'); sensitive.push(token);
    insist(mail.includes(base + '/accept-invitation?token='), 'Invitation origin mismatch');
    insist(mail.includes('DEVELOPER') && mail.includes('48 hours') && mail.includes('FeatureFlag Platform'), 'Invitation email content mismatch');
    return { token, url: base + '/accept-invitation?token=' + token };
  }, 'local SMTP message');
}
async function fillInputs(values) {
  await browser.evaluate('(() => { const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set; const values = ' + JSON.stringify(values) + '; for(const [selector,value] of Object.entries(values)){ const input=document.querySelector(selector); if(!input) throw new Error("Missing input"); set.call(input,value); input.dispatchEvent(new Event("input",{bubbles:true})); } return true; })()');
}
async function clickButton(text) {
  await browser.evaluate('(() => {const button=[...document.querySelectorAll("button")].find(b=>b.textContent.includes(' + JSON.stringify(text) + ')); if(!button) throw new Error("Missing button"); button.click(); return true;})()');
}
async function browserInvite(email) {
  await clickButton('+ Invite Member');
  await waitUntil(() => browser.evaluate('Boolean(document.querySelector("form input[type=email]"))'), 'invite form');
  await fillInputs({'form input[type=text]':'Invitation Smoke', 'form input[type=email]':email});
  await browser.evaluate('document.querySelector("form button[type=submit]").click(); true');
}
try {
  await waitUntil(async () => (await fetch(base, {signal:AbortSignal.timeout(4000)})).ok, 'Compose frontend', 180000);
  record('Frontend through Nginx and Gateway', {origin:base});
  await waitUntil(async () => (await api('/auth/invitations/validate?token=fixture-readiness')).status===200, 'Gateway Auth discovery', 180000);
  const owner = await login(settings.BOOTSTRAP_OWNER_EMAIL, settings.BOOTSTRAP_OWNER_PASSWORD);
  sensitive.push(owner);
  const pending = (await api('/members/invite', {method:'POST',token:owner,body:{name:'Invitation acceptance test',email:recipient,role:'DEVELOPER'},expected:201})).data;
  insist(pending.status==='PENDING' && pending.emailDeliveryConfirmed===true, 'Invitation must commit and confirm SMTP acceptance');
  record('Invitation creation confirms SMTP acceptance separately from PENDING');
  const firstMail=await messageFor(recipient);
  record('Local SMTP sink receives the intended recipient and acceptance URL');
  const second = (await api('/members/invitations/'+pending.id+'/resend',{method:'POST',token:owner,expected:200})).data;
  const secondMail = await messageFor(recipient,2);
  insist((await api('/auth/invitations/validate?token='+encodeURIComponent(firstMail.token),{expected:200})).data.valid===false, 'Old resend token stayed valid');
  insist((await api('/auth/invitations/validate?token='+encodeURIComponent(secondMail.token),{expected:200})).data.valid===true, 'Replacement token invalid');
  record('Resend replaces token; previous URL is rejected');
  const list = (await api('/members/invitations?size=100',{token:owner,expected:200})).data.content;
  insist(list.every(item=>!('token' in item)&&!('tokenHash' in item)&&!('acceptanceUrl' in item)&&!('emailDeliveryConfirmed' in item)), 'List exposed secret or transient delivery state');
  record('Invitation list contains no token or transient delivery state');
  browser = await browserSession();
  await browser.call('Page.navigate',{url:base+'/login'});
  await waitUntil(()=>browser.evaluate('Boolean(document.querySelector("input[type=email]"))'),'browser login form');
  await fillInputs({'input[type=email]':settings.BOOTSTRAP_OWNER_EMAIL,'input[type=password]':settings.BOOTSTRAP_OWNER_PASSWORD});
  await browser.evaluate('document.querySelector("button[type=submit]").click(); true');
  await waitUntil(()=>browser.evaluate('location.pathname==="/dashboard" && Boolean(localStorage.getItem("authUser"))'),'browser OWNER login');
  await browser.call('Page.navigate',{url:base+'/members'});
  await waitUntil(()=>browser.evaluate('document.body.innerText.includes("Invite Member")'),'Members page');
  const browserRecipient='invitation-browser-'+suffix+'@example.test';
  await browserInvite(browserRecipient);
  await waitUntil(()=>browser.evaluate('document.body.innerText.includes("Invitation sent successfully")'),'browser delivery success');
  await messageFor(browserRecipient);
  record('Browser Send Invitation shows confirmed sending; SMTP sink receives mail');
  await fetch(mailApi + '/fail',{method:'POST'});
  const failureRecipient='invitation-failure-'+suffix+'@example.test';
  await browserInvite(failureRecipient);
  await waitUntil(()=>browser.evaluate('document.querySelector("[role=alert]")?.textContent.includes("could not be confirmed")'),'browser delivery warning');
  const failedList=(await api('/members/invitations?size=100',{token:owner,expected:200})).data.content;
  insist(failedList.some(i=>i.email===failureRecipient && i.status==='PENDING'), 'SMTP failure lost committed invitation');
  record('SMTP 451 produces a visible warning while invitation remains PENDING');
  await fetch(mailApi + '/recover',{method:'POST'});
  await browser.evaluate('(() => {const row=[...document.querySelectorAll("tr")].find(r=>r.textContent.includes('+JSON.stringify(failureRecipient)+')); [...row.querySelectorAll("button")].find(b=>b.textContent.trim()==="Resend").click(); return true;})()');
  await waitUntil(()=>browser.evaluate('document.body.innerText.includes("Invitation sent successfully") && !document.querySelector("[role=alert]")'),'browser resend recovery');
  const acceptedMail=await messageFor(failureRecipient);
  record('Browser Resend recovers after SMTP restoration');
  await browser.call('Page.navigate',{url:acceptedMail.url});
  await waitUntil(()=>browser.evaluate('document.querySelectorAll("input[type=password]").length===2'),'Accept Invitation page');
  record('Email link opens acceptance route and validates one-time token');
  await browser.evaluate('(() => {const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set; for(const input of document.querySelectorAll("input[type=password]")){set.call(input,'+JSON.stringify(password)+');input.dispatchEvent(new Event("input",{bubbles:true}));} return true;})()');
  await browser.evaluate('document.querySelector("form button[type=submit]").click(); true');
  await waitUntil(()=>browser.evaluate('document.body.innerText.includes("Account Ready!")'),'acceptance completion');
  record('Recipient creates password through the real browser form');
  const acceptedList=(await api('/members/invitations?size=100',{token:owner,expected:200})).data.content;
  insist(acceptedList.some(i=>i.email===failureRecipient&&i.status==='ACCEPTED'),'Invitation not accepted');
  const members=(await api('/members?size=100',{token:owner,expected:200})).data.content;
  insist(members.some(i=>i.email===failureRecipient && i.role==='DEVELOPER' && i.enabled),'Member absent');
  const memberToken=await login(failureRecipient,password); sensitive.push(memberToken);
  record('ACCEPTED invitation, enabled member and new-member login verified');
  insist((await api('/auth/invitations/validate?token='+encodeURIComponent(acceptedMail.token),{expected:200})).data.valid===false,'Accepted token reusable');
  await api('/auth/invitations/accept',{method:'POST',body:{token:acceptedMail.token,password,confirmPassword:password},expected:409});
  const acceptedRow=acceptedList.find(i=>i.email===failureRecipient&&i.status==='ACCEPTED');
  await api('/members/invitations/'+acceptedRow.id+'/resend',{method:'POST',token:owner,expected:400});
  await api('/members/invitations/'+second.id+'/revoke',{method:'POST',token:owner,expected:200});
  await api('/members/invitations/'+second.id+'/resend',{method:'POST',token:owner,expected:400});
  record('Accepted token cannot be reused; accepted/revoked resend rejected');
  const notificationLogs=await processRun('docker',['logs','--since',startedAt,await ownedContainer('notification-service')], undefined, process.env, true);
  const authLogs=await processRun('docker',['logs','--since',startedAt,await ownedContainer('auth-service')], undefined, process.env, true);
  insist(notificationLogs.includes('errorType=MailSendException') && notificationLogs.includes('Invitation email delivered'), 'SMTP error/success evidence missing');
  insist(authLogs.includes('Invitation email delivery failed after invitation commit') && authLogs.includes('delivery confirmed by Notification Service'),'Auth dispatch evidence missing');
  insist(!sensitive.some(value=>(authLogs+notificationLogs).includes(value)), 'Sensitive value appeared in service logs');
  record('Auth and Notification recover without logging credentials');
  console.log(JSON.stringify({status:'PASS',checks:results.length,externalEmailSent:false}));
} catch(error) {
  console.log(JSON.stringify({status:'FAIL',check:error.message}));process.exitCode=1;
} finally {
  await fetch(mailApi + '/recover',{method:'POST'}).catch(()=>{});
  if(browser){await browser.call('Browser.close').catch(()=>{});browser.ws.close();browser.child.kill();}
}
