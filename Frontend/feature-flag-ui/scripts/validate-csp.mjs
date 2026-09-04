import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const projectRoot = resolve(import.meta.dirname, "..");
const nginx = readFileSync(resolve(projectRoot, "nginx.conf"), "utf8");
const indexHtml = readFileSync(resolve(projectRoot, "dist", "index.html"), "utf8");

const requiredDirectives = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "frame-ancestors 'none'",
  "form-action 'self'",
];

for (const directive of requiredDirectives) {
  if (!nginx.includes(directive)) {
    throw new Error(`CSP is missing required directive: ${directive}`);
  }
}

if (nginx.includes("'unsafe-eval'")) {
  throw new Error("CSP must not permit unsafe-eval");
}

const scriptDirective = nginx.match(/script-src[^;"]*/)?.[0] ?? "";
if (scriptDirective.includes("'unsafe-inline'")) {
  throw new Error("CSP script-src must not permit unsafe-inline");
}

for (const match of indexHtml.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
  if (!/\bsrc\s*=/.test(match[1]) || match[2].trim().length > 0) {
    throw new Error("Built index contains an inline script blocked by CSP");
  }
}

for (const match of indexHtml.matchAll(/\b(?:src|href)=["']([^"']+)["']/gi)) {
  const asset = match[1];
  if (/^(?:https?:)?\/\//i.test(asset)) {
    throw new Error(`Built index references an external asset: ${asset}`);
  }
}

console.log("CSP and built frontend assets validated");
