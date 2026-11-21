import express from "express";
import dns from "node:dns/promises";
import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";

try {
  process.loadEnvFile?.();
} catch {
  // .env is optional
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.join(__dirname, "dist");
const port = Number(process.env.PORT) || 3000;
const host = "0.0.0.0";

const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const DRIVE_ID_PATTERN = /^[A-Za-z0-9_-]{10,128}$/;
const MAX_REDIRECTS = 5;

function readFirebaseConfig() {
  const appletConfigPath = path.join(__dirname, "firebase-applet-config.json");
  if (fs.existsSync(appletConfigPath)) {
    try {
      return JSON.parse(fs.readFileSync(appletConfigPath, "utf8"));
    } catch {
      // fall through to environment config
    }
  }

  return {
    apiKey: process.env.FIREBASE_API_KEY || "",
    authDomain: process.env.FIREBASE_AUTH_DOMAIN || "foodguide-fb333.firebaseapp.com",
    databaseURL: process.env.FIREBASE_DATABASE_URL || "https://foodguide-fb333-default-rtdb.firebaseio.com",
    projectId: process.env.FIREBASE_PROJECT_ID || "foodguide-fb333",
    storageBucket: process.env.FIREBASE_STORAGE_BUCKET || "foodguide-fb333.firebasestorage.app",
    messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID || "1012988825364",
    appId: process.env.FIREBASE_APP_ID || "1:1012988825364:web:023656da0f9718e8113c01"
  };
}

function sanitizeFileName(rawName) {
  const base = String(rawName || "")
    .replace(/[^a-zA-Z0-9._-]+/g, "-")
    .replace(/^[.-]+|[.-]+$/g, "");
  if (!base) return "app.apk";
  return base.toLowerCase().endsWith(".apk") ? base : `${base}.apk`;
}

function extractDriveInfo(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    if (parsed.protocol !== "https:" || parsed.username || parsed.password) return null;
    if (parsed.hostname === "drive.google.com" || parsed.hostname === "docs.google.com") {
      const id =
        parsed.pathname.match(/^\/file\/d\/([\w-]+)(?:\/|$)/)?.[1] ||
        parsed.searchParams.get("id");
      if (!id || !DRIVE_ID_PATTERN.test(id)) return null;
      const resourceKey = parsed.searchParams.get("resourcekey");
      return {
        id,
        resourceKey: resourceKey && ID_PATTERN.test(resourceKey) ? resourceKey : ""
      };
    }
    if (parsed.hostname === "drive.usercontent.google.com") {
      const id = parsed.searchParams.get("id");
      if (!id || !DRIVE_ID_PATTERN.test(id)) return null;
      const resourceKey = parsed.searchParams.get("resourcekey");
      return {
        id,
        resourceKey: resourceKey && ID_PATTERN.test(resourceKey) ? resourceKey : ""
      };
    }
    return null;
  } catch {
    return null;
  }
}

function isDriveHost(hostname) {
  return (
    hostname === "drive.google.com" ||
    hostname === "drive.usercontent.google.com" ||
    hostname === "docs.google.com" ||
    hostname.endsWith(".googleusercontent.com")
  );
}

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const parts = ip.split(".").map(Number);
    const [a, b] = parts;
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
    if (a >= 224) return true;
    return false;
  }
  if (net.isIPv6(ip)) {
    const lower = ip.toLowerCase();
    if (lower === "::" || lower === "::1") return true;
    if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80:")) return true;
    if (lower.startsWith("::ffff:")) {
      const mapped = lower.slice(7);
      if (net.isIPv4(mapped)) return isPrivateIp(mapped);
    }
    return false;
  }
  return true;
}

async function assertSafePublicHttpsUrl(rawUrl, { driveOnly = false } = {}) {
  const parsed = new URL(rawUrl);
  if (parsed.protocol !== "https:" || parsed.username || parsed.password) {
    throw new Error("Only HTTPS URLs without credentials are allowed.");
  }
  const hostname = parsed.hostname.toLowerCase();
  if (
    hostname === "localhost" ||
    hostname.endsWith(".localhost") ||
    hostname.endsWith(".local") ||
    hostname.endsWith(".internal")
  ) {
    throw new Error("Blocked private hostname.");
  }
  if (driveOnly && !isDriveHost(hostname)) {
    if (hostname === "accounts.google.com") {
      const err = new Error("Google Drive file is private. Set sharing to 'Anyone with the link'.");
      err.status = 403;
      throw err;
    }
    throw new Error("Blocked non-Drive redirect host.");
  }
  if (net.isIP(hostname)) {
    if (isPrivateIp(hostname)) throw new Error("Blocked private IP address.");
    return parsed;
  }
  const records = await dns.lookup(hostname, { all: true });
  if (!records.length || records.some((record) => isPrivateIp(record.address))) {
    throw new Error("Blocked private network destination.");
  }
  return parsed;
}

async function fetchWithSafeRedirects(initialUrl, { driveOnly = false, headers = {}, signal } = {}) {
  let currentUrl = initialUrl;
  let cookieJar = [];

  for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
    await assertSafePublicHttpsUrl(currentUrl, { driveOnly });
    const reqHeaders = {
      "User-Agent": "AppNest-Downloader/1.0",
      ...headers
    };
    if (cookieJar.length) {
      reqHeaders.Cookie = cookieJar.join("; ");
    }

    const response = await fetch(currentUrl, {
      method: "GET",
      headers: reqHeaders,
      redirect: "manual",
      signal
    });

    const setCookies = response.headers.getSetCookie?.() ?? [];
    if (setCookies.length) {
      for (const raw of setCookies) {
        const pair = raw.split(";")[0]?.trim();
        if (pair) cookieJar.push(pair);
      }
    }

    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get("location");
      if (!location) throw new Error("Redirect missing location header.");
      currentUrl = new URL(location, currentUrl).href;
      continue;
    }

    return { response, currentUrl, cookieJar };
  }

  throw new Error("Too many redirects.");
}

function parseDriveConfirmationForm(html, baseUrl) {
  const formMatch = html.match(/<form[^>]*id=["']download-form["'][^>]*action=["']([^"']+)["'][^>]*>([\s\S]*?)<\/form>/i);
  if (formMatch) {
    const action = formMatch[1].replace(/&amp;/g, "&");
    const target = new URL(action, baseUrl);
    const inputRegex = /<input[^>]*type=["']hidden["'][^>]*>/gi;
    for (const tag of formMatch[2].match(inputRegex) ?? []) {
      const name = tag.match(/name=["']([^"']+)["']/i)?.[1];
      const value = tag.match(/value=["']([^"']*)["']/i)?.[1];
      if (name && value !== undefined) {
        target.searchParams.set(name, value);
      }
    }
    return target.href;
  }

  const linkMatch = html.match(/href=["']([^"']*confirm=[^"']+)["']/i);
  if (linkMatch) {
    return new URL(linkMatch[1].replace(/&amp;/g, "&"), baseUrl).href;
  }
  return null;
}

async function fetchDriveStream(id, resourceKey, rangeHeader, signal) {
  const directUrl = new URL("https://drive.usercontent.google.com/download");
  directUrl.searchParams.set("id", id);
  directUrl.searchParams.set("export", "download");
  directUrl.searchParams.set("confirm", "t");
  if (resourceKey) directUrl.searchParams.set("resourcekey", resourceKey);

  const forwardHeaders = rangeHeader ? { Range: rangeHeader } : {};
  let result = await fetchWithSafeRedirects(directUrl.href, {
    driveOnly: true,
    headers: forwardHeaders,
    signal
  });

  const contentType = (result.response.headers.get("content-type") || "").toLowerCase();
  if (result.response.ok && contentType.includes("text/html")) {
    const html = await result.response.text();
    const confirmedUrl = parseDriveConfirmationForm(html, result.currentUrl);
    if (!confirmedUrl) {
      const isAuthGate = /accounts\.google\.com|ServiceLogin/i.test(result.currentUrl + html);
      const err = new Error(
        isAuthGate
          ? "Google Drive file is private. Set sharing to 'Anyone with the link'."
          : "Google Drive did not return a downloadable APK file."
      );
      err.status = isAuthGate ? 403 : 502;
      throw err;
    }

    const cookieHeader = result.cookieJar.length ? { Cookie: result.cookieJar.join("; ") } : {};
    result = await fetchWithSafeRedirects(confirmedUrl, {
      driveOnly: true,
      headers: { ...forwardHeaders, ...cookieHeader },
      signal
    });

    const retryType = (result.response.headers.get("content-type") || "").toLowerCase();
    if (retryType.includes("text/html")) {
      const err = new Error("Google Drive returned a webpage instead of the APK file.");
      err.status = 502;
      throw err;
    }
  }

  return result.response;
}

async function pipeUpstreamResponse(upstream, res, fallbackFileName) {
  if (!upstream.ok || !upstream.body) {
    const status = upstream.status === 404 ? 404 : upstream.status === 403 ? 403 : 502;
    return res.status(status).json({ error: "Upstream APK download failed." });
  }

  const contentType = upstream.headers.get("content-type") || "application/vnd.android.package-archive";
  if (contentType.toLowerCase().includes("text/html")) {
    return res.status(502).json({ error: "The download link returned an HTML page instead of an APK file." });
  }

  res.status(upstream.status);
  res.setHeader("Content-Type", contentType.includes("octet-stream") || contentType.includes("android") ? contentType : "application/vnd.android.package-archive");
  res.setHeader("Content-Disposition", `attachment; filename="${sanitizeFileName(fallbackFileName)}"`);
  res.setHeader("Cache-Control", "no-store");

  for (const header of ["content-length", "accept-ranges", "content-range"]) {
    const val = upstream.headers.get(header);
    if (val) res.setHeader(header, val);
  }

  const nodeStream = Readable.fromWeb(upstream.body);
  nodeStream.on("error", () => {
    if (!res.headersSent) res.status(502).end();
    else res.destroy();
  });
  res.on("close", () => {
    nodeStream.destroy();
  });
  nodeStream.pipe(res);
}

const app = express();

app.get("/api/firebase-config", (_req, res) => {
  res.json(readFirebaseConfig());
});

app.get("/api/download/drive/:id", async (req, res) => {
  const { id } = req.params;
  if (!DRIVE_ID_PATTERN.test(id)) {
    return res.status(400).json({ error: "Invalid Google Drive file ID." });
  }
  const resourceKey = typeof req.query.resourcekey === "string" && ID_PATTERN.test(req.query.resourcekey)
    ? req.query.resourcekey
    : "";
  const fileName = sanitizeFileName(req.query.filename);
  const controller = new AbortController();
  req.on("close", () => controller.abort());

  try {
    const upstream = await fetchDriveStream(id, resourceKey, req.headers.range, controller.signal);
    return await pipeUpstreamResponse(upstream, res, fileName);
  } catch (error) {
    if (error.name === "AbortError") return;
    return res.status(error.status || 502).json({ error: error.message || "Google Drive download failed." });
  }
});

app.get("/api/download/app/:appId", async (req, res) => {
  const { appId } = req.params;
  if (!ID_PATTERN.test(appId)) {
    return res.status(400).json({ error: "Invalid app identifier." });
  }

  const config = readFirebaseConfig();
  if (!config.databaseURL) {
    return res.status(500).json({ error: "Firebase database URL is not configured." });
  }

  const controller = new AbortController();
  req.on("close", () => controller.abort());

  try {
    const dbBase = new URL(config.databaseURL);
    if (dbBase.protocol !== "https:") {
      return res.status(500).json({ error: "Invalid Firebase database URL." });
    }
    const recordUrl = new URL(`/apps/${encodeURIComponent(appId)}.json`, dbBase);
    const metaResp = await fetch(recordUrl.href, { signal: controller.signal });
    if (!metaResp.ok) {
      return res.status(404).json({ error: "App listing not found." });
    }
    const appData = await metaResp.json();
    if (!appData || appData.published === false) {
      return res.status(404).json({ error: "App listing is unavailable." });
    }

    const fileName = sanitizeFileName(req.query.filename || appData.name);
    const preferDrive = req.query.source === "drive";
    const candidateUrl = preferDrive
      ? appData.driveUrl || appData.downloadUrl
      : appData.downloadUrl || appData.driveUrl;

    if (!candidateUrl || typeof candidateUrl !== "string") {
      return res.status(404).json({ error: "No download URL found for this app." });
    }

    const driveInfo = extractDriveInfo(candidateUrl);
    if (driveInfo) {
      const upstream = await fetchDriveStream(
        driveInfo.id,
        driveInfo.resourceKey,
        req.headers.range,
        controller.signal
      );
      return await pipeUpstreamResponse(upstream, res, fileName);
    }

    const forwardHeaders = req.headers.range ? { Range: req.headers.range } : {};
    const { response: upstream } = await fetchWithSafeRedirects(candidateUrl, {
      driveOnly: false,
      headers: forwardHeaders,
      signal: controller.signal
    });
    return await pipeUpstreamResponse(upstream, res, fileName);
  } catch (error) {
    if (error.name === "AbortError") return;
    return res.status(error.status || 502).json({ error: error.message || "Download proxy failed." });
  }
});

app.get("/manifest.webmanifest", (_req, res) => {
  res.type("application/manifest+json");
  res.sendFile(path.join(distDir, "manifest.webmanifest"));
});

app.get("/sw.js", (_req, res) => {
  res.type("application/javascript");
  res.setHeader("Service-Worker-Allowed", "/");
  res.setHeader("Cache-Control", "no-cache");
  res.sendFile(path.join(distDir, "sw.js"));
});

app.use(express.static(distDir));

app.get("/admin", (_req, res) => {
  res.sendFile(path.join(distDir, "admin.html"));
});

app.use((req, res, next) => {
  if (req.method !== "GET" && req.method !== "HEAD") return next();
  res.sendFile(path.join(distDir, "index.html"));
});

app.listen(port, host, () => {
  console.log(`AppNest server listening on http://${host}:${port}`);
});
