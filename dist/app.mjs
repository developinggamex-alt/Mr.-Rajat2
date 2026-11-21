import {connectFirebase, drive, driveId, https} from "./firebase.mjs";

const screen = document.querySelector("#screen");
const nav = document.querySelector("#nav");
const notice = document.querySelector("#notice");
const $ = (selector) => document.querySelector(selector);
const esc = (value = "") => String(value ?? "").replace(/[&<>"']/g, (char) =>
  ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[char]);

function url(value) {
  return https(value)?.href ?? "";
}
function stored(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
  catch { return fallback; }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch {}
}

const AVATAR_COLORS = [
  { hex: "#3b44f6", label: "Indigo" },
  { hex: "#0284c7", label: "Ocean" },
  { hex: "#059669", label: "Emerald" },
  { hex: "#d97706", label: "Amber" },
  { hex: "#e11d48", label: "Rose" },
  { hex: "#7c3aed", label: "Violet" }
];

let apps = [], query = "", category = "", sortMode = "trending", searchSort = "relevance";
let state = "loading", loginState = "loading";
let user, database, auth, api, authApi, timer, accountMode = "signin", catalogListener;
let recent = stored("appnest-recent", []);
let history = Object.assign(Object.create(null), stored("appnest-downloads", {}));
const active = new Map(), files = new Map();
let installed = Object.assign(Object.create(null), stored("appnest-installed", {}));
const preferences = stored("appnest-preferences", {});
let saved = Array.isArray(stored("appnest-saved", [])) ? stored("appnest-saved", []) : [];
let viewed = Array.isArray(stored("appnest-viewed", [])) ? stored("appnest-viewed", []) : [];
let reviews = Object.assign(Object.create(null), stored("appnest-reviews", {}));
let profileMeta = Object.assign({ guestName: "", bio: "", photoURL: "", avatarColor: "#3b44f6" }, stored("appnest-profile", {}));
let downloadTab = "active", updateAlerted = false, draftStar = 5, profileEditing = false, pendingPermissionAction = null;
let deferredInstallPrompt = null;
let appInstalled = Boolean(
  globalThis.AppNestAndroid ||
  window.matchMedia?.("(display-mode: standalone)").matches ||
  window.navigator?.standalone === true
);
const isIOSDevice = /iphone|ipad|ipod/i.test(navigator.userAgent || "");

function applyTheme(dark) {
  document.documentElement.classList.toggle("dark", Boolean(dark));
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", dark ? "#0f111c" : "#ffffff");
  try { globalThis.AppNestAndroid?.setDarkMode?.(Boolean(dark)); } catch {}
}
applyTheme(Boolean(preferences.darkMode));

const sheetEl = document.querySelector("#sheet");
const installBtn = document.querySelector("#install-app");
const offlineBanner = document.querySelector("#offline-banner");

function syncInstallUi() {
  if (installBtn) installBtn.hidden = appInstalled;
}
function syncOnlineUi() {
  if (offlineBanner) offlineBanner.hidden = navigator.onLine !== false;
}
syncInstallUi();
syncOnlineUi();

if ("serviceWorker" in navigator && !globalThis.AppNestAndroid) {
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}

window.addEventListener("beforeinstallprompt", event => {
  event.preventDefault();
  deferredInstallPrompt = event;
  syncInstallUi();
  if (["profile", "settings"].includes(route()[0])) render();
});
window.addEventListener("appinstalled", () => {
  appInstalled = true;
  deferredInstallPrompt = null;
  syncInstallUi();
  render();
  toast("AppNest installed on your home screen.");
});
window.addEventListener("online", () => { syncOnlineUi(); toast("Back online."); });
window.addEventListener("offline", () => { syncOnlineUi(); });

async function triggerAppInstall() {
  if (appInstalled) {
    toast("AppNest is already running as an installed app.");
    return;
  }
  if (deferredInstallPrompt) {
    deferredInstallPrompt.prompt();
    try {
      const choice = await deferredInstallPrompt.userChoice;
      if (choice?.outcome === "accepted") {
        appInstalled = true;
        deferredInstallPrompt = null;
        syncInstallUi();
        render();
      }
    } catch {}
    return;
  }
  if (isIOSDevice) {
    openSheet(
      '<div class="sheet-icon">' + ico.download + '</div>' +
      '<h2>Install AppNest on iPhone / iPad</h2>' +
      '<p>Add AppNest to your home screen so it opens full-screen like a native app:</p>' +
      '<div class="sheet-list">' +
      '<div class="sheet-row"><span>1. Tap <strong>Share</strong> in Safari’s bottom bar</span></div>' +
      '<div class="sheet-row"><span>2. Tap <strong>Add to Home Screen</strong></span></div>' +
      '</div>' +
      '<div class="sheet-actions"><button type="button" class="primary" data-sheet-close="1">Got it</button></div>'
    );
    return;
  }
  openSheet(
    '<div class="sheet-icon">' + ico.download + '</div>' +
    '<h2>Install AppNest as an Android App</h2>' +
    '<p>Install this website directly onto your Android home screen so it runs full-screen like a native app without a browser bar:</p>' +
    '<div class="sheet-list">' +
    '<div class="sheet-row"><span>1. Open browser menu (<strong>⋮</strong>)</span></div>' +
    '<div class="sheet-row"><span>2. Tap <strong>Install app</strong> or <strong>Add to Home screen</strong></span></div>' +
    '</div>' +
    '<div class="sheet-actions"><button type="button" class="primary" data-sheet-close="1">Got it</button></div>'
  );
}

function openSheet(html) {
  if (!sheetEl) return;
  sheetEl.innerHTML = '<div class="sheet-card" role="dialog" aria-modal="true"><div class="sheet-handle" aria-hidden="true"></div>' + html + '</div>';
  sheetEl.hidden = false;
  sheetEl.querySelector("button, a")?.focus();
}
function closeSheet() {
  if (!sheetEl) return;
  sheetEl.hidden = true;
  sheetEl.innerHTML = "";
}

const driveInfo = app => driveId(app.driveUrl) || driveId(app.downloadUrl);
const direct = app => url(app.downloadUrl) && !driveId(app.downloadUrl) ? url(app.downloadUrl) : "";
const driveLink = app => drive(app.driveUrl) || drive(app.downloadUrl);
const downloadable = app => Boolean(direct(app) || driveInfo(app));

function notify(message) {
  toast(message);
  if (preferences.notifications && globalThis.Notification?.permission === "granted") {
    try { new Notification("AppNest", {body:message}); } catch {}
  }
}
function toast(message) {
  notice.textContent = message;
  clearTimeout(timer);
  timer = setTimeout(() => { notice.textContent = ""; }, 4300);
}

const href = (id) => "#app/" + encodeURIComponent(id);
const empty = (title, detail = "") => '<div class="empty"><strong>' + esc(title) + '</strong>' + esc(detail) + '</div>';
const size = (bytes) => bytes >= 1073741824 ? (bytes / 1073741824).toFixed(2) + " GB" : bytes >= 1048576 ? (bytes / 1048576).toFixed(1) + " MB" : (bytes / 1024).toFixed(1) + " KB";

function effectiveRating(app) {
  const local = reviews[app.id];
  if (Number.isFinite(app.rating) && local) {
    const count = Math.max(1, Number(app.reviewCount) || 1);
    return Number(((app.rating * count + local.rating) / (count + 1)).toFixed(1));
  }
  if (Number.isFinite(app.rating)) return app.rating;
  return local ? local.rating : null;
}
function effectiveReviewCount(app) {
  const base = Number.isFinite(app.reviewCount) ? app.reviewCount : 0;
  return base + (reviews[app.id] ? 1 : 0);
}

const info = (app) => {
  const rating = effectiveRating(app);
  return [Number.isFinite(rating) ? "★ " + rating : "", app.sizeBytes > 0 ? size(app.sizeBytes) : ""].filter(Boolean).join(" · ");
};

function imageMarkup(value, alt = "") {
  return url(value) ? '<img src="' + esc(url(value)) + '" alt="' + esc(alt) + '" decoding="async" referrerpolicy="no-referrer">' : "";
}
function thumbnail(app) {
  return '<span class="thumb" aria-hidden="true">' + (imageMarkup(app.iconUrl) || esc(app.name.slice(0, 2).toUpperCase())) + '</span>';
}

/* --- Algorithms: Trending, Search Relevance + Fuzzy Matching, Recommendations, SemVer --- */

function trendingScore(app) {
  const dl = Math.log10(Math.max(0, Number(app.downloadCount) || 0) + 1) * 14;
  const rating = (effectiveRating(app) ?? 3.5) * 6;
  const revs = Math.log10(effectiveReviewCount(app) + 1) * 4;
  const feat = app.featured === true ? 6 : 0;
  const ageDays = Number.isFinite(app.updatedAt) ? Math.max(0, (Date.now() - app.updatedAt) / 86400000) : 30;
  const recency = Math.max(0, 10 - ageDays * 0.25);
  return dl + rating + revs + feat + recency;
}

function editDistanceLimited(a, b, maxDist = 2) {
  if (Math.abs(a.length - b.length) > maxDist) return maxDist + 1;
  const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let curr = [i];
    let rowMin = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      const val = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
      curr.push(val);
      if (val < rowMin) rowMin = val;
    }
    if (rowMin > maxDist) return maxDist + 1;
    for (let k = 0; k < curr.length; k++) prev[k] = curr[k];
  }
  return prev[b.length];
}

function scoreSearchMatch(app, rawQuery) {
  const q = rawQuery.trim().toLowerCase();
  if (!q) return 1;
  const title = String(app.name ?? "").toLowerCase();
  const cat = String(app.category ?? "").toLowerCase();
  const dev = String(app.developer ?? "").toLowerCase();
  const tag = String(app.tagline ?? "").toLowerCase();
  const desc = String(app.description ?? "").toLowerCase();

  let score = 0;
  if (title === q) score += 140;
  else if (title.startsWith(q)) score += 90;
  else if (title.includes(q)) score += 60;

  const tokens = q.split(/\s+/).filter(Boolean);
  const titleWords = title.split(/[\s._-]+/).filter(Boolean);
  let matchedTokens = 0;

  for (const token of tokens) {
    let tokenHit = false;
    if (title.includes(token)) { score += 35; tokenHit = true; }
    else if (token.length >= 4 && titleWords.some(w => editDistanceLimited(token, w, token.length >= 6 ? 2 : 1) <= (token.length >= 6 ? 2 : 1))) {
      score += 22;
      tokenHit = true;
    }
    if (cat.includes(token)) { score += 26; tokenHit = true; }
    if (dev.includes(token)) { score += 22; tokenHit = true; }
    if (tag.includes(token)) { score += 16; tokenHit = true; }
    if (desc.includes(token)) { score += 8; tokenHit = true; }
    if (tokenHit) matchedTokens++;
  }

  if (matchedTokens === 0) return 0;
  if (matchedTokens === tokens.length) score += 20;
  return score + Math.min(15, trendingScore(app) * 0.25);
}

function sortApps(list, mode) {
  const copy = [...list];
  if (mode === "rating") {
    return copy.sort((a, b) => (effectiveRating(b) ?? 0) - (effectiveRating(a) ?? 0) || (b.reviewCount ?? 0) - (a.reviewCount ?? 0));
  }
  if (mode === "downloads") {
    return copy.sort((a, b) => (b.downloadCount ?? 0) - (a.downloadCount ?? 0));
  }
  if (mode === "newest") {
    return copy.sort((a, b) => (Number(b.updatedAt) || 0) - (Number(a.updatedAt) || 0));
  }
  if (mode === "name") {
    return copy.sort((a, b) => String(a.name).localeCompare(String(b.name)));
  }
  return copy.sort((a, b) => trendingScore(b) - trendingScore(a));
}

function recommendApps(limit = 4) {
  if (!apps.length) return [];
  const catWeights = Object.create(null);
  const devWeights = Object.create(null);
  const addSignal = (app, weight) => {
    if (!app) return;
    if (app.category) catWeights[app.category] = (catWeights[app.category] || 0) + weight;
    if (app.developer) devWeights[app.developer] = (devWeights[app.developer] || 0) + weight * 0.8;
  };

  for (const id of Object.keys(installed)) addSignal(apps.find(a => a.id === id), 3.0);
  for (const id of saved) addSignal(apps.find(a => a.id === id), 2.5);
  for (const id of Object.keys(history)) addSignal(apps.find(a => a.id === id), 2.0);
  for (const entry of viewed) addSignal(apps.find(a => a.id === entry.id), 1.0);

  const hasSignals = Object.keys(catWeights).length > 0 || recent.length > 0;
  const candidates = apps.filter(app => !installed[app.id]);
  const pool = candidates.length ? candidates : apps;

  return pool.map(app => {
    let affinity = (catWeights[app.category] || 0) * 14 + (devWeights[app.developer] || 0) * 10;
    let reason = "";
    if (catWeights[app.category]) reason = "Matches your " + app.category + " apps";
    else if (devWeights[app.developer]) reason = "More from " + app.developer;

    const searchBoost = recent.some(term =>
      [app.name, app.category, app.tagline].some(f => String(f ?? "").toLowerCase().includes(term.toLowerCase()))
    );
    if (searchBoost) {
      affinity += 18;
      if (!reason) reason = "Based on recent searches";
    }
    if (!reason) {
      reason = hasSignals ? "Popular in catalog" : "Trending on AppNest";
    }
    const totalScore = affinity + trendingScore(app) * 0.65;
    return { app, score: totalScore, reason };
  })
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
}

function recordView(app) {
  if (!app?.id) return;
  viewed = [{ id: app.id, name: app.name, category: app.category || "", at: Date.now() },
    ...viewed.filter(item => item.id !== app.id)].slice(0, 12);
  save("appnest-viewed", viewed);
}

function toggleSaved(id) {
  if (saved.includes(id)) {
    saved = saved.filter(item => item !== id);
    toast("Removed from saved apps.");
  } else {
    saved = [id, ...saved];
    toast("Saved to your profile.");
  }
  save("appnest-saved", saved);
}

function openApkDb() {
  return new Promise((resolve, reject) => {
    if (!globalThis.indexedDB) { reject(new Error("no-idb")); return; }
    const req = indexedDB.open("appnest-apk-db", 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("apks")) db.createObjectStore("apks", { keyPath: "id" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function cacheApkBlob(id, name, blob) {
  try {
    const db = await openApkDb();
    await new Promise((resolve) => {
      const tx = db.transaction("apks", "readwrite");
      tx.objectStore("apks").put({ id, name, blob, at: Date.now() });
      tx.oncomplete = resolve;
      tx.onerror = resolve;
    });
    db.close();
  } catch {}
}
async function loadApkBlob(id) {
  try {
    const db = await openApkDb();
    const record = await new Promise((resolve) => {
      const tx = db.transaction("apks", "readonly");
      const req = tx.objectStore("apks").get(id);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
    db.close();
    return record?.blob ? { blob: record.blob, name: record.name } : null;
  } catch { return null; }
}
async function clearApkDb() {
  try {
    const db = await openApkDb();
    await new Promise((resolve) => {
      const tx = db.transaction("apks", "readwrite");
      tx.objectStore("apks").clear();
      tx.oncomplete = resolve;
      tx.onerror = resolve;
    });
    db.close();
  } catch {}
}

function launchExternalUrl(hrefUrl) {
  if (!hrefUrl) return;
  const a = document.createElement("a");
  a.href = hrefUrl;
  a.target = "_blank";
  a.rel = "noopener noreferrer";
  document.body.append(a);
  a.click();
  a.remove();
}

function appInstallStatus(app) {
  if (!app?.id) return "none";
  if (active.has(app.id)) return "active";
  let androidInstalled = false;
  try { androidInstalled = Boolean(globalThis.AppNestAndroid?.isAppInstalled?.(app.id)); } catch {}
  const trackedVer = installed[app.id]?.version ?? history[app.id]?.version;
  if ((installed[app.id] || history[app.id] || androidInstalled) && newer(app.version, trackedVer)) return "update";
  if (installed[app.id] || androidInstalled) return "installed";
  if (history[app.id]) return "downloaded";
  return "none";
}

function appActionButton(app, extraClass = "small-action") {
  const st = appInstallStatus(app);
  const id = esc(app.id);
  const name = esc(app.name);
  if (st === "active") return '<button class="subtle ' + extraClass + '" data-download="' + id + '" aria-label="Downloading ' + name + '">Downloading…</button>';
  if (st === "update") return '<button class="primary ' + extraClass + '" data-download="' + id + '" aria-label="Update ' + name + '">Update</button>';
  if (st === "installed" || st === "downloaded") return '<button class="tonal ' + extraClass + '" data-open-app="' + id + '" aria-label="Open ' + name + '">Open</button>';
  if (downloadable(app)) return '<button class="primary ' + extraClass + '" data-download="' + id + '" aria-label="Download ' + name + '">Download</button>';
  if (url(app.openUrl)) return '<a class="tonal ' + extraClass + '" href="' + esc(url(app.openUrl)) + '" target="_blank" rel="noopener noreferrer">Open</a>';
  return "";
}

async function openApp(id) {
  const app = apps.find(entry => entry.id === id) || { id, name: history[id]?.name || installed[id]?.name || "App" };
  const fileName = apkFileName(app);
  const openLink = url(app.openUrl);
  try {
    if (globalThis.AppNestAndroid?.openAppById?.(id, fileName, openLink)) {
      if (!installed[id]) {
        installed[id] = { name: app.name, version: app.version || history[id]?.version || "", at: Date.now() };
        save("appnest-installed", installed);
        render();
      }
      return;
    }
  } catch {}
  if (openLink) {
    launchExternalUrl(openLink);
    return;
  }
  let cached = files.get(id);
  if (!cached) {
    cached = await loadApkBlob(id);
    if (cached) files.set(id, cached);
  }
  if (cached) {
    saveFile(id, cached);
    if (!installed[id]) {
      installed[id] = { name: app.name, version: app.version || history[id]?.version || "", at: Date.now() };
      save("appnest-installed", installed);
      render();
    }
    toast("Opening " + (cached.name || fileName) + "…");
    return;
  }
  openSheet(
    '<div class="sheet-icon">' + ico.download + '</div>' +
    '<h2>' + esc(app.name) + '</h2>' +
    '<p>' + (installed[id]
      ? 'This app is already downloaded and marked installed. Open it from your device’s app drawer or Downloads folder, or re-download the APK.'
      : 'This app was downloaded earlier. Open the saved APK from your device Downloads folder, or download it again.') + '</p>' +
    '<div class="sheet-actions">' +
    (downloadable(app) ? '<button type="button" class="primary" data-sheet-action="redownload-app" data-app-id="' + esc(id) + '">Download APK again</button>' : '') +
    '<button type="button" class="subtle" data-sheet-close="1">Close</button>' +
    '</div>'
  );
}

/* --- Catalog & Views --- */

function categories() {
  const labels = [...new Set(apps.map(app => app.category).filter(Boolean))].sort();
  if (!labels.length) return "";
  return '<div class="chips" aria-label="Categories">' + ["", ...labels].map(label =>
    '<button class="chip" data-category="' + esc(label) + '" aria-pressed="' + (category === label) + '">' + esc(label || "All") + '</button>').join("") + '</div>';
}

function sortControl(current, options, attrName) {
  return '<div class="sort-bar" role="group" aria-label="Sort apps">' + options.map(([value, label]) =>
    '<button class="sort-btn" ' + attrName + '="' + value + '" aria-pressed="' + (current === value) + '">' + label + '</button>'
  ).join("") + '</div>';
}

function listing(app) {
  return '<article class="row">' + thumbnail(app) + '<a class="copy" href="' + href(app.id) + '"><strong>' +
    esc(app.name) + '</strong><small>' + esc([app.category, info(app)].filter(Boolean).join(" · ")) +
    '</small></a>' + appActionButton(app) + '</article>';
}

function catalogStatus() {
  if (state === "loading") return empty("Loading catalog", "Connecting to Firebase…");
  if (state === "denied") return empty("Catalog access restricted", user ? "Your account does not have access." : "Sign in from Profile or check the catalog access settings.");
  if (state === "error") return empty("Couldn’t connect to Firebase") + '<p class="actions"><button class="subtle" data-action="retry">Retry</button></p>';
  return empty("No apps published yet", "Published apps and games will appear here.");
}

function grid(items, message) {
  return state !== "ready" || !apps.length ? catalogStatus() : items.length ?
    '<div class="cards">' + items.map(listing).join("") + '</div>' : empty(message);
}

function searchResults() {
  const term = query.trim();
  const filtered = apps
    .filter(app => !category || app.category === category)
    .map(app => ({ app, score: scoreSearchMatch(app, term) }))
    .filter(entry => entry.score > 0);

  let ordered;
  if (searchSort === "relevance" && term) {
    ordered = filtered.sort((a, b) => b.score - a.score).map(e => e.app);
  } else {
    ordered = sortApps(filtered.map(e => e.app), searchSort === "relevance" ? "trending" : searchSort);
  }
  return grid(ordered, "No matching apps");
}

function home() {
  const featured = apps.find(app => app.featured === true);
  const filtered = apps.filter(app => !category || app.category === category);
  const popular = sortApps(filtered, sortMode);
  const feature = featured ? '<section class="hero">' + imageMarkup(featured.bannerUrl) + '<small>Featured</small><h2>' +
    esc(featured.name) + '</h2><p>' + esc(featured.tagline) + '</p><a class="primary" href="' + href(featured.id) + '">View app</a></section>' :
    '<section class="hero"><h2>Discover. Download. Enjoy.</h2><p>Explore apps and games on AppNest.</p></section>';
  const recs = (saved.length || Object.keys(history).length || Object.keys(installed).length || viewed.length) ? recommendApps(3) : [];
  const recSection = recs.length ? '<h2>Picked for you</h2><div class="cards">' + recs.map(({ app }) => listing(app)).join("") + '</div>' : "";
  const sortTabs = [["trending","Trending"],["rating","Top Rated"],["downloads","Popular"],["newest","Newest"]];

  return '<h1>' + (displayNameLabel() ? "Welcome, " + esc(displayNameLabel()) : "Discover apps &amp; games") +
    '</h1><a class="search fake-search" href="#search">⌕ &nbsp; Search apps, games…</a>' + feature +
    (apps.length ? '<h2>Categories</h2>' + categories() : "") +
    recSection +
    '<div class="section-bar"><h2>Catalog</h2>' + sortControl(sortMode, sortTabs, "data-sort") + '</div>' +
    grid(popular, "No apps in this category");
}

function search() {
  const sortTabs = [["relevance","Relevance"],["rating","Top Rated"],["downloads","Downloads"],["name","A–Z"]];
  return '<h1>Search</h1><form id="search-form"><input class="search" type="search" aria-label="Search apps and games" name="query" list="catalog-suggestions" placeholder="Search apps, developers, categories…" value="' +
    esc(query) + '"><datalist id="catalog-suggestions">' + apps.slice(0,80).map(app =>
      '<option value="' + esc(app.name) + '"></option>').join("") + '</datalist></form>' +
    (recent.length ? '<div class="section-bar"><h2>Recent searches</h2><button type="button" class="text-button" data-action="clear-searches">Clear</button></div><div class="chips">' + recent.map(term =>
      '<button class="chip" data-recent="' + esc(term) + '">' + esc(term) + '</button>').join("") + '</div>' : "") +
    categories() +
    '<div class="section-bar"><h2>Results</h2>' + sortControl(searchSort, sortTabs, "data-search-sort") + '</div><div id="results">' + searchResults() + '</div>';
}

function details(id) {
  const app = apps.find(entry => entry.id === id);
  if (!app) return '<a class="subtle" href="#home">Back to home</a><h2>App details</h2>' + (state === "ready" ? empty("App unavailable") : catalogStatus());
  const previews = Object.values(app.screenshots ?? {}).filter(item => typeof item === "string" && url(item));
  const rating = effectiveRating(app);
  const revCount = effectiveReviewCount(app);
  const stats = [
    Number.isFinite(rating) ? "★ " + rating : "",
    app.sizeBytes > 0 ? size(app.sizeBytes) : "",
    revCount > 0 ? revCount.toLocaleString() + " reviews" : "",
    Number.isFinite(app.downloadCount) ? app.downloadCount.toLocaleString() + " downloads" : "",
    app.version ? "Version " + app.version : ""
  ].filter(Boolean);
  const isSaved = saved.includes(id);
  const st = appInstallStatus(app);
  const isInstalled = st === "installed" || Boolean(installed[id]);
  const existingReview = reviews[id];
  const primaryActions =
    st === "active"
      ? '<button class="subtle" data-download="' + esc(id) + '">Downloading…</button>'
      : st === "update"
        ? '<button class="primary" data-download="' + esc(id) + '">Update APK</button><button class="tonal" data-open-app="' + esc(id) + '">Open</button>'
        : (st === "installed" || st === "downloaded")
          ? '<button class="primary" data-open-app="' + esc(id) + '">Open</button>' +
            (downloadable(app) ? '<button class="subtle" data-download="' + esc(id) + '">Reinstall</button>' : '')
          : downloadable(app)
            ? '<button class="primary" data-download="' + esc(id) + '">Download APK</button>' +
              (direct(app) && driveLink(app) ? '<button class="subtle" data-drive="' + esc(id) + '">Download via Drive</button>' : '')
            : (url(app.openUrl) ? '<a class="primary" href="' + esc(url(app.openUrl)) + '" target="_blank" rel="noopener noreferrer">Open</a>' : '<span class="muted">Download unavailable</span>');

  return '<a class="subtle" href="#home">Back to home</a><div class="detail">' + thumbnail(app) + '<div><h1>' +
    esc(app.name) + '</h1><p class="muted">' + esc([app.developer, app.category].filter(Boolean).join(" · ")) + '</p></div></div>' +
    '<div class="stats">' + stats.map(stat => '<span>' + esc(stat) + '</span>').join("") + '</div>' +
    '<div class="actions">' +
    primaryActions +
    '<button class="subtle" data-save-toggle="' + esc(id) + '" aria-pressed="' + isSaved + '">' + (isSaved ? "Saved" : "Save app") + '</button>' +
    (isInstalled ? '<button class="subtle" data-unmark="' + esc(id) + '">Installed ✓</button>' : '<button class="subtle" data-mark="' + esc(id) + '">Mark installed</button>') +
    '</div>' +
    (previews.length ? '<h2>Previews</h2><div class="preview">' + previews.map((item,i) => imageMarkup(item, app.name + " screenshot " + (i + 1))).join("") + '</div>' : "") +
    (app.description ? '<h2>About this app</h2><p class="long-copy">' + esc(app.description) + '</p>' : "") +
    '<h2>Your rating &amp; review</h2><section class="panel"><form id="review-form" data-app-id="' + esc(id) + '">' +
    '<div class="star-picker" role="group" aria-label="Select star rating">' +
    [1,2,3,4,5].map(star => '<button type="button" class="star-btn' + (star <= (existingReview?.rating ?? draftStar) ? ' filled' : '') + '" data-star="' + star + '" aria-label="' + star + ' stars">★</button>').join("") +
    '</div><label>Review note<textarea class="search" name="reviewText" rows="3" maxlength="400" placeholder="Share how this app performs on your device…">' +
    esc(existingReview?.text || "") + '</textarea></label><div class="actions"><button class="primary">' + (existingReview ? "Update review" : "Post review") + '</button>' +
    (existingReview ? '<button type="button" class="subtle" data-delete-review="' + esc(id) + '">Remove review</button>' : '') + '</div></form>' +
    (existingReview ? '<div class="review-item"><strong>' + esc(existingReview.author || "You") + ' · ★ ' + existingReview.rating + '</strong>' +
      (existingReview.text ? '<p class="muted">' + esc(existingReview.text) + '</p>' : '') + '</div>' : '') + '</section>';
}

function downloads() {
  const tabs = [["active","Downloading"],["installed","Installed"],["history","History"]];
  const updatesList = availableUpdates();
  let content;
  if (downloadTab === "active") {
    content = [...active.values()].map(task => '<article class="panel"><h3>' + esc(task.app.name) +
      '</h3><p id="status-' + task.index + '">' + esc(task.status) +
      '</p><progress class="meter" aria-label="Download progress" id="progress-' + task.index +
      '"></progress><div class="actions"><button class="subtle" data-pause="' + esc(task.app.id) + '">' +
      (task.paused ? "Resume" : "Pause") + '</button><button class="subtle" data-cancel="' + esc(task.app.id) +
      '">Cancel</button></div></article>').join("") || empty("No active downloads");
  } else if (downloadTab === "installed") {
    const updateAllBar = updatesList.length > 1
      ? '<div class="actions"><button class="primary" data-action="update-all">Update all (' + updatesList.length + ')</button></div>'
      : "";
    content = updateAllBar + (Object.entries(installed).map(([id,item]) => {
      const app = apps.find(entry => entry.id === id);
      return '<article class="row"><div class="copy"><strong>' + esc(item.name) +
        '</strong><small>' + esc(item.version ? "Version " + item.version : "Installed on this device") + '</small></div>' +
        '<button class="tonal small-action" data-open-app="' + esc(id) + '">Open</button>' +
        (app && newer(app.version,item.version) && downloadable(app) ? '<button class="primary small-action" data-download="' + esc(id) + '">Update</button>' : '') +
        '<button class="subtle small-action" data-unmark="' + esc(id) + '">Remove</button></article>';
    }).join("") || empty("No installed apps yet", "Completed downloads are automatically ready to open here."));
  } else {
    const entries = Object.entries(history).sort(([,a],[,b]) => b.at - a.at);
    const clearBar = entries.length ? '<div class="actions"><button class="subtle" data-action="clear-history">Clear history</button></div>' : "";
    content = clearBar + (entries.map(([id,item]) => {
      const app = apps.find(entry => entry.id === id);
      return '<article class="row"><div class="copy"><strong>' + esc(item.name) + '</strong><small class="num">' +
        esc(size(item.bytes)) + ' · ' + esc(new Date(item.at).toLocaleDateString()) + '</small></div>' +
        '<button class="tonal small-action" data-open-app="' + esc(id) + '">Open</button>' +
        (app && downloadable(app) ? '<button class="subtle small-action" data-download="' + esc(id) + '">Reinstall</button>' : "") +
        '</article>';
    }).join("") || empty("No completed in-app downloads", "Downloads completed inside AppNest appear here."));
  }
  return '<h1>My Downloads</h1><div class="chips section-title" aria-label="Download views">' + tabs.map(([key,label]) =>
    '<button class="chip" data-tab="' + key + '" aria-pressed="' + (key === downloadTab) + '">' + label +
    '</button>').join("") + '</div><div class="download-list">' + content + '</div>';
}

function displayNameLabel() {
  return (user?.displayName || profileMeta.guestName || "").trim();
}

function initialsFor(name) {
  const clean = (name || "AN").trim();
  const parts = clean.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return clean.slice(0, 2).toUpperCase();
}

const ico = {
  chevron: '<span class="chevron" aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></span>',
  back: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>',
  gear: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  download: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>',
  arrowUp: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>',
  arrowUpCircle: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><polyline points="16 12 12 8 8 12"/><line x1="12" y1="16" x2="12" y2="8"/></svg>',
  shield: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  bell: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
  refresh: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>',
  wifi: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.55a11 11 0 0 1 14.08 0"/><path d="M1.42 9a16 16 0 0 1 21.16 0"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/></svg>',
  folder: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>',
  shieldCheck: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>',
  help: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  moon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>',
  google: '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17Z"/><path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.11-6.72-4.96H1.29v3.14C3.26 21.3 7.31 24 12 24Z"/><path fill="#FBBC05" d="M5.28 14.24c-.24-.72-.38-1.49-.38-2.24s.14-1.52.38-2.24V6.62H1.29C.47 8.24 0 10.06 0 12s.47 3.76 1.29 5.38l3.99-3.14Z"/><path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.62l3.99 3.14c.95-2.85 3.6-4.96 6.72-4.96Z"/></svg>',
  info: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>'
};

function profile() {
  const activeName = displayNameLabel() || (user ? "Your account" : "Guest profile");
  const totalBytes = Object.values(history).reduce((sum, item) => sum + (Number(item.bytes) || 0), 0);
  const installedCount = Object.keys(installed).length;
  const downloadCount = Object.keys(history).length;
  const savedApps = saved.map(id => apps.find(a => a.id === id)).filter(Boolean);
  const recs = recommendApps(4);
  const updatesList = availableUpdates();
  const recentViewedApps = viewed.map(v => apps.find(a => a.id === v.id)).filter(Boolean).slice(0, 4);
  const avatarBg = AVATAR_COLORS.some(c => c.hex === profileMeta.avatarColor) ? profileMeta.avatarColor : "#3b44f6";
  const subtitle = user ? (user.email || "Signed in") : "Local device profile";
  const photoUrl = user ? url(user.photoURL || profileMeta.photoURL) : "";
  const avatarInner = photoUrl
    ? '<img src="' + esc(photoUrl) + '" alt="' + esc(activeName) + '" referrerpolicy="no-referrer">'
    : esc(initialsFor(activeName));

  const topHtml = '<h1 class="page-title">Profile</h1>' +
    '<section class="profile-top">' +
    '<div class="avatar" style="background:' + esc(avatarBg) + '">' + avatarInner + '</div>' +
    '<div class="profile-meta"><h2>' + esc(activeName) + '</h2>' +
    '<p class="profile-email">' + esc(subtitle) + '</p>' +
    (profileMeta.bio ? '<p class="profile-bio">' + esc(profileMeta.bio) + '</p>' : '') +
    '<button type="button" class="edit-pill-btn" data-action="toggle-edit" aria-expanded="' + profileEditing + '">' +
    (profileEditing ? "Close editor" : "Edit profile") + '</button>' +
    '</div></section>';

  const statsHtml = '<section class="stats-3col" aria-label="Account overview stats">' +
    '<div class="stat-card"><div class="stat-num">' + downloadCount + '</div><div class="stat-label">Apps<br>downloaded</div></div>' +
    '<div class="stat-card"><div class="stat-num">' + installedCount + '</div><div class="stat-label">Installed<br>apps</div></div>' +
    '<div class="stat-card"><div class="stat-num">' + esc(size(totalBytes)) + '</div><div class="stat-label">Storage<br>used</div></div>' +
    '</section>';

  const actionStackHtml = '<section class="action-stack" aria-label="Quick navigation">' +
    (!appInstalled ? '<button type="button" class="action-card" data-action="install-pwa"><span class="action-icon">' + ico.download + '</span><span class="action-label">Install AppNest App</span><span class="action-sub">Android &amp; Web</span>' + ico.chevron + '</button>' : '') +
    '<a class="action-card" href="#settings"><span class="action-icon">' + ico.gear + '</span><span class="action-label">Settings</span>' + ico.chevron + '</a>' +
    '<a class="action-card" href="#downloads"><span class="action-icon">' + ico.download + '</span><span class="action-label">My Downloads</span>' +
    (active.size ? '<span class="action-sub num">' + active.size + ' active</span>' : '') + ico.chevron + '</a>' +
    '<a class="action-card" href="#updates"><span class="action-icon">' + ico.arrowUp + '</span><span class="action-label">App Updates</span>' +
    (updatesList.length ? '<span class="action-sub num">' + updatesList.length + ' ready</span>' : '') + ico.chevron + '</a>' +
    '</section>';

  const editorHtml = profileEditing ? '<section class="profile-section">' +
    '<div class="section-head"><h2>Edit profile</h2><span>' + (user ? "Account &amp; local" : "Saved on device") + '</span></div>' +
    '<form id="edit-profile">' +
    '<label>Display name<input class="search" name="name" required maxlength="60" placeholder="Enter your name" value="' + esc(displayNameLabel()) + '"></label>' +
    '<label>Bio<input class="search" name="bio" maxlength="120" placeholder="Android tester, gamer, or developer" value="' + esc(profileMeta.bio || "") + '"></label>' +
    '<label>Avatar color</label>' +
    '<div class="swatches" role="group" aria-label="Avatar color">' +
    AVATAR_COLORS.map(c => '<button type="button" class="swatch" style="background:' + c.hex + '" data-avatar-color="' + c.hex + '" aria-label="' + c.label + '" aria-pressed="' + (avatarBg === c.hex) + '"></button>').join("") +
    '</div>' +
    '<button class="primary">Save profile</button>' +
    '</form></section>' : "";

  const heading = accountMode === "signup" ? "Create account" : "Sign in";
  const authHtml = !user ? '<section class="profile-section">' +
    '<div class="section-head"><h2>' + heading + '</h2><span>AppNest account</span></div>' +
    (loginState === "ready" ? '<button type="button" class="google-btn" data-action="google-signin">' + ico.google + '<span>Continue with Google (Gmail)</span></button>' +
      '<div class="auth-divider">or continue with email</div>' +
      '<form id="account"><label>Email<input class="search" name="email" type="email" required autocomplete="email"></label><label>Password<input class="search" name="password" type="password" required autocomplete="' +
      (accountMode === "signup" ? 'new-password" minlength="6"' : 'current-password"') + '></label><div class="actions"><button class="primary">' +
      heading + '</button><button type="button" class="subtle" data-action="mode">' + (accountMode === "signup" ? "Use existing account" : "Create account") +
      '</button></div><button type="button" class="text-button" data-action="password">Show password</button>' +
      (accountMode === "signin" ? ' <button type="button" class="text-button" data-action="reset">Reset password</button>' : '') +
      '</form>' : '<p class="muted">' + (loginState === "loading" ? "Connecting to Firebase Authentication…" : "Firebase Authentication unavailable.") + '</p>') +
    '</section>' : "";

  const maintenanceHtml = '<section class="profile-section">' +
    '<div class="section-head"><h2>Updates &amp; storage</h2><span class="num">' + updatesList.length + ' updates</span></div>' +
    '<div class="mini-list">' +
    '<div class="mini-row"><div class="copy"><strong>App updates</strong><small>' +
    (updatesList.length ? updatesList.length + " newer version(s) ready" : "All tracked apps are up to date") +
    '</small></div><div class="row-actions">' +
    (updatesList.length ? '<button type="button" class="primary" data-action="update-all">Update all</button>' : '<a class="subtle" href="#updates">Check</a>') +
    '</div></div>' +
    '<div class="mini-row"><div class="copy"><strong>Download history</strong><small class="num">' + downloadCount + ' items · ' + files.size + ' cached</small></div>' +
    '<div class="row-actions">' + (downloadCount ? '<button type="button" class="subtle" data-action="clear-history">Clear</button>' : '') + '</div></div>' +
    '<div class="mini-row"><div class="copy"><strong>Recent searches</strong><small class="num">' + recent.length + ' saved terms</small></div>' +
    '<div class="row-actions">' + (recent.length ? '<button type="button" class="subtle" data-action="clear-searches">Clear</button>' : '') + '</div></div>' +
    '</div></section>';

  const savedHtml = '<section class="profile-section">' +
    '<div class="section-head"><h2>Saved apps</h2><span class="num">' + savedApps.length + '</span></div>' +
    (savedApps.length ? '<div class="mini-list">' + savedApps.map(app =>
      '<div class="mini-row">' + thumbnail(app) + '<a class="copy" href="' + href(app.id) + '"><strong>' + esc(app.name) + '</strong><small>' + esc([app.category, info(app)].filter(Boolean).join(" · ")) + '</small></a>' +
      '<div class="row-actions">' +
      appActionButton(app) +
      '<button type="button" class="subtle small-action" data-save-toggle="' + esc(app.id) + '">Remove</button></div></div>'
    ).join("") + '</div>' : '<p class="muted">Bookmark apps from any details page to keep them handy here.</p>') +
    '</section>';

  const recsHtml = '<section class="profile-section">' +
    '<div class="section-head"><h2>Recommended for you</h2><span>Smart match</span></div>' +
    (recs.length ? '<div class="mini-list">' + recs.map(({ app, reason }) =>
      '<div class="mini-row">' + thumbnail(app) + '<a class="copy" href="' + href(app.id) + '"><strong>' + esc(app.name) + '</strong><small>' + esc(reason) + (effectiveRating(app) ? ' · ★ ' + effectiveRating(app) : '') + '</small></a>' +
      appActionButton(app) + '</div>'
    ).join("") + '</div>' : '<p class="muted">Recommendations appear as the catalog loads.</p>') +
    '</section>';

  const viewedHtml = recentViewedApps.length ? '<section class="profile-section">' +
    '<div class="section-head"><h2>Recently viewed</h2><span class="num">' + recentViewedApps.length + '</span></div>' +
    '<div class="mini-list">' + recentViewedApps.map(app =>
      '<div class="mini-row">' + thumbnail(app) + '<a class="copy" href="' + href(app.id) + '"><strong>' + esc(app.name) + '</strong><small>' + esc([app.category, app.developer].filter(Boolean).join(" · ")) + '</small></a></div>'
    ).join("") + '</div></section>' : "";

  return '<div class="profile-shell">' + topHtml + statsHtml + actionStackHtml +
    '<div class="profile-workspace">' +
    '<div>' + editorHtml + authHtml + maintenanceHtml + '</div>' +
    '<div>' + savedHtml + recsHtml + viewedHtml + '</div>' +
    '</div></div>';
}

function settings() {
  const switchRow = (icon, key, label, detail = "") =>
    '<label class="settings-row"><span class="settings-ico">' + icon + '</span><span class="settings-copy"><strong>' + label + '</strong>' +
    (detail ? '<small>' + detail + '</small>' : '') +
    '</span><span class="switch"><input type="checkbox" data-preference="' + key + '"' + (preferences[key] ? ' checked' : '') + '><span class="slider" aria-hidden="true"></span></span></label>';

  const unknownAllowed = Boolean(preferences.unknownApps);
  return '<div class="settings-shell">' +
    '<div class="settings-topbar"><a class="back-btn" href="#profile" aria-label="Back to Profile">' + ico.back + '</a><h1>Settings</h1></div>' +
    '<p class="settings-group-label">Appearance</p>' +
    '<section class="settings-group">' +
    switchRow(ico.moon, "darkMode", "Dark mode", "Easier viewing in low light") +
    '</section>' +
    '<p class="settings-group-label">Downloads &amp; Updates</p>' +
    '<section class="settings-group">' +
    '<a class="settings-row" href="#downloads"><span class="settings-ico">' + ico.download + '</span><span class="settings-copy"><strong>My Downloads</strong></span>' + ico.chevron + '</a>' +
    '<a class="settings-row" href="#updates"><span class="settings-ico">' + ico.arrowUpCircle + '</span><span class="settings-copy"><strong>App Updates</strong></span>' + ico.chevron + '</a>' +
    '</section>' +
    '<p class="settings-group-label">Permissions</p>' +
    '<section class="settings-group">' +
    '<button type="button" class="settings-row" data-action="open-unknown-permission"><span class="settings-ico">' + ico.shield + '</span><span class="settings-copy"><strong>Install unknown apps</strong><small>Allow APK downloads from AppNest</small></span><span class="settings-status' + (unknownAllowed ? '' : ' off') + '">' + (unknownAllowed ? "Allowed" : "Not allowed") + '</span>' + ico.chevron + '</button>' +
    switchRow(ico.bell, "notifications", "Notifications") +
    switchRow(ico.refresh, "updates", "Auto-update apps") +
    switchRow(ico.wifi, "wifi", "Download over Wi-Fi only") +
    '</section>' +
    '<section class="settings-group">' +
    (!appInstalled ? '<button type="button" class="settings-row" data-action="install-pwa"><span class="settings-ico">' + ico.download + '</span><span class="settings-copy"><strong>Install AppNest App</strong><small>Add to Android or mobile home screen</small></span><span class="settings-status">Install</span>' + ico.chevron + '</button>' : '') +
    '<button type="button" class="settings-row" data-action="open-storage"><span class="settings-ico">' + ico.folder + '</span><span class="settings-copy"><strong>Storage &amp; location</strong><small>Manage local cache and download history</small></span>' + ico.chevron + '</button>' +
    '<a class="settings-row" href="#privacy"><span class="settings-ico">' + ico.shieldCheck + '</span><span class="settings-copy"><strong>Privacy Policy</strong></span>' + ico.chevron + '</a>' +
    '<a class="settings-row" href="#help"><span class="settings-ico">' + ico.help + '</span><span class="settings-copy"><strong>Help &amp; Support</strong></span>' + ico.chevron + '</a>' +
    '<button type="button" class="settings-row" data-action="open-about"><span class="settings-ico">' + ico.info + '</span><span class="settings-copy"><strong>About AppNest</strong></span><span class="settings-version">v2.0</span>' + ico.chevron + '</button>' +
    '</section>' +
    (user ? '<button type="button" class="logout-cta" data-action="logout">Log out</button>' : '<a class="logout-cta" href="#profile">Sign in</a>') +
    '</div>';
}

function privacy() {
  return '<div class="settings-shell"><div class="settings-topbar"><a class="back-btn" href="#settings" aria-label="Back to Settings">' + ico.back + '</a><h1>Privacy Policy</h1></div><section class="panel"><p>Firebase Authentication handles account email, Google sign-in and profile name. AppNest uses browser storage for preferences, recent searches, saved apps, reviews, marked-installed apps and download history. Linked file and image hosts receive the requests needed to load their content.</p><p class="intro muted">You can remove browser-stored AppNest data through Settings → Storage &amp; location or your browser’s site settings.</p></section></div>';
}

function help() {
  return '<div class="settings-shell"><div class="settings-topbar"><a class="back-btn" href="#settings" aria-label="Back to Settings">' + ico.back + '</a><h1>Help &amp; Support</h1></div><section class="settings-group">' +
    '<details class="settings-details" open><summary class="settings-row"><span class="settings-ico">' + ico.download + '</span><span class="settings-copy"><strong>Downloading an APK</strong></span>' + ico.chevron + '</summary><p class="settings-detail-body">Tap Download to grant AppNest unknown-app permission and queue the APK directly inside AppNest with live progress.</p></details>' +
    '<details class="settings-details"><summary class="settings-row"><span class="settings-ico">' + ico.shield + '</span><span class="settings-copy"><strong>Installing on Android</strong></span>' + ico.chevron + '</summary><p class="settings-detail-body">Open the saved APK from your browser’s downloads or file manager. If Android asks, enable Install unknown apps for your browser, then mark the app installed in AppNest.</p></details>' +
    '<details class="settings-details"><summary class="settings-row"><span class="settings-ico">' + ico.help + '</span><span class="settings-copy"><strong>Sign-in problems</strong></span>' + ico.chevron + '</summary><p class="settings-detail-body">Use Google (Gmail) sign-in or Reset password on the Profile screen if you forgot your password.</p></details>' +
    '</section><div class="actions"><button type="button" class="subtle" data-action="open-unknown-permission">Manage download permission</button><button type="button" class="subtle" data-action="open-storage">Manage storage</button></div></div>';
}

function newer(next, previous) {
  const clean = v => String(v ?? "").trim().replace(/^v/i, "").split(/[-+]/)[0];
  const aStr = clean(next), bStr = clean(previous);
  if (!/^\d+(\.\d+)*$/.test(aStr) || !/^\d+(\.\d+)*$/.test(bStr)) return false;
  const a = aStr.split(".").map(Number), b = bStr.split(".").map(Number);
  for (let i = 0; i < Math.max(a.length,b.length); i++) {
    const diff = (a[i] ?? 0) - (b[i] ?? 0);
    if (diff) return diff > 0;
  }
  return false;
}

const availableUpdates = () => apps.filter(app => newer(app.version, installed[app.id]?.version ?? history[app.id]?.version));
function checkUpdates() {
  if (preferences.updates && !updateAlerted && availableUpdates().length) {
    updateAlerted = true;
    notify("App updates are available. Open Settings → App Updates.");
  }
}
function updates() {
  const list = availableUpdates();
  return '<h1>App Updates</h1><p class="intro muted">Newer catalog versions for your tracked apps.</p>' +
    (list.length > 1 ? '<div class="actions"><button class="primary" data-action="update-all">Update all (' + list.length + ')</button></div>' : '') +
    grid(list, "No newer versions available");
}

function route() {
  const [page = "", rawId = ""] = location.hash.slice(1).split("/");
  let id;
  try { id = decodeURIComponent(rawId); } catch { id = ""; }
  return [page || "home", id];
}

function render() {
  const [page,id] = route();
  if (page === "app" && id) {
    const app = apps.find(entry => entry.id === id);
    if (app) recordView(app);
  }
  const views = {home, search, downloads, profile, settings, updates, privacy, help};
  screen.innerHTML = page === "app" ? details(id) : Object.hasOwn(views,page) ? views[page]() : home();
  [...nav.querySelectorAll("a")].forEach(link => {
    if (link.hash === "#" + page) link.setAttribute("aria-current","page");
    else link.removeAttribute("aria-current");
  });
  if (page === "downloads") active.forEach(updateTask);
}

function updateTask(task) {
  const label = $("#status-" + task.index), progress = $("#progress-" + task.index);
  if (!label || !progress) return;
  label.textContent = task.status;
  if (task.total) { progress.max = task.total; progress.value = task.bytes; }
}

function saveFile(id, file = files.get(id)) {
  if (!file) return;
  if (globalThis.AppNestAndroid?.installApkBase64ForApp || globalThis.AppNestAndroid?.installApkBase64) {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const base64 = result.includes(",") ? result.split(",")[1] : result;
      if (!base64) return;
      if (globalThis.AppNestAndroid.installApkBase64ForApp) {
        globalThis.AppNestAndroid.installApkBase64ForApp(id, base64, file.name);
      } else {
        globalThis.AppNestAndroid.installApkBase64(base64, file.name);
      }
    };
    reader.readAsDataURL(file.blob);
    return;
  }
  const objectUrl = URL.createObjectURL(file.blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = file.name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
}

let taskIndex = 0;
function wifiAllowed() {
  if (!preferences.wifi) return true;
  const conn = navigator.connection;
  if (conn?.saveData || (conn?.type && conn.type !== "wifi" && conn.type !== "ethernet")) {
    toast("Wi-Fi-only downloads are enabled. Connect to Wi-Fi or turn off the setting.");
    return false;
  }
  return true;
}

function requestUnknownAppsPermission(onAllowed = null) {
  pendingPermissionAction = onAllowed;
  const allowed = Boolean(preferences.unknownApps);
  openSheet(
    '<div class="sheet-icon">' + ico.shield + '</div>' +
    '<h2>Allow unknown app downloads?</h2>' +
    '<p>AppNest downloads APK packages directly to your device. Allow unknown app downloads here, and enable <strong>Install unknown apps</strong> for your browser in Android Settings if prompted during installation.</p>' +
    '<div class="sheet-actions">' +
    (allowed
      ? '<button type="button" class="subtle" data-sheet-action="deny-unknown">Revoke permission</button><button type="button" class="primary" data-sheet-close="1">Keep allowed</button>'
      : '<button type="button" class="subtle" data-sheet-action="deny-unknown">Deny</button><button type="button" class="primary" data-sheet-action="grant-unknown">' + (onAllowed ? 'Allow &amp; Download' : 'Allow') + '</button>') +
    '</div>'
  );
}

function openStorageSheet() {
  const totalBytes = Object.values(history).reduce((sum, item) => sum + (Number(item.bytes) || 0), 0);
  const downloadCount = Object.keys(history).length;
  openSheet(
    '<div class="sheet-icon">' + ico.folder + '</div>' +
    '<h2>Storage &amp; device data</h2>' +
    '<p>APK files are saved to your browser’s downloads folder. AppNest never accesses your location.</p>' +
    '<div class="sheet-list">' +
    '<div class="sheet-row"><span>Downloaded history</span><strong class="num">' + downloadCount + ' apps · ' + esc(size(totalBytes)) + '</strong></div>' +
    '<div class="sheet-row"><span>Cached APKs in memory</span><strong class="num">' + files.size + '</strong></div>' +
    '<div class="sheet-row"><span>Saved apps &amp; reviews</span><strong class="num">' + saved.length + ' saved · ' + Object.keys(reviews).length + ' reviews</strong></div>' +
    '<div class="sheet-row"><span>Recent searches</span><strong class="num">' + recent.length + ' terms</strong></div>' +
    '</div>' +
    '<div class="sheet-actions">' +
    (files.size ? '<button type="button" class="subtle" data-sheet-action="clear-cache">Clear cache</button>' : '') +
    (downloadCount ? '<button type="button" class="subtle" data-sheet-action="clear-history">Clear history</button>' : '') +
    '<button type="button" class="primary" data-sheet-close="1">Done</button>' +
    '</div>'
  );
}

function openAboutSheet() {
  const statusLabel = state === "ready" ? "Connected (" + apps.length + " apps)" : state === "loading" ? "Connecting…" : "Offline";
  openSheet(
    '<div class="sheet-icon">' + ico.info + '</div>' +
    '<h2>About AppNest</h2>' +
    '<p>Mobile-first Android APK store with direct in-app downloads, smart recommendations, and Firebase sync.</p>' +
    '<div class="sheet-list">' +
    '<div class="sheet-row"><span>Version</span><strong class="num">v2.0</strong></div>' +
    '<div class="sheet-row"><span>Catalog status</span><strong>' + esc(statusLabel) + '</strong></div>' +
    '<div class="sheet-row"><span>Account</span><strong>' + esc(user ? (user.displayName || user.email || "Signed in") : "Guest mode") + '</strong></div>' +
    '<div class="sheet-row"><span>Theme</span><strong>' + (preferences.darkMode ? "Dark mode" : "Light mode") + '</strong></div>' +
    '</div>' +
    '<div class="sheet-actions"><button type="button" class="primary" data-sheet-close="1">Close</button></div>'
  );
}

function apkFileName(app) {
  return (app.name.replace(/[^a-zA-Z0-9._-]+/g,"-").replace(/^[.-]+|[.-]+$/g,"") || "app") + ".apk";
}
function proxyDownloadUrl(app, preferDrive = false) {
  const fileName = apkFileName(app);
  const info = driveInfo(app);
  if ((preferDrive || !direct(app)) && info) {
    const params = new URLSearchParams({ filename: fileName });
    if (info.resourceKey) params.set("resourcekey", info.resourceKey);
    return "/api/download/drive/" + encodeURIComponent(info.id) + "?" + params.toString();
  }
  const params = new URLSearchParams({ filename: fileName });
  if (preferDrive) params.set("source", "drive");
  return "/api/download/app/" + encodeURIComponent(app.id) + "?" + params.toString();
}
async function fetchApkStream(source, task, controller) {
  const response = await fetch(source,{signal:controller.signal});
  if (!response.ok) {
    let message = "Download unavailable";
    try { const err = await response.json(); if (err?.error) message = err.error; } catch {}
    throw new Error(message);
  }
  if (!response.body) throw new Error("Download unavailable");
  const type = response.headers.get("content-type") || "";
  if (type.includes("text/") || type.includes("json")) throw new Error("The source did not return an APK file.");
  task.total = Number(response.headers.get("content-length")) || 0;
  task.bytes = 0;
  const reader = response.body.getReader(), chunks = [], started = performance.now();
  for (;;) {
    if (task.paused) await new Promise(resolve => { task.resume = resolve; });
    if (controller.signal.aborted) throw new DOMException("Aborted","AbortError");
    const {done,value} = await reader.read();
    if (done) break;
    chunks.push(value);
    task.bytes += value.length;
    const speed = task.bytes / Math.max(1,(performance.now()-started)/1000);
    const percent = task.total ? Math.min(100, Math.floor(task.bytes * 100 / task.total)) + "% · " : "";
    task.status = task.paused ? "Paused · " + size(task.bytes) : percent + size(task.bytes) +
      (task.total ? " / " + size(task.total) : "") + " · " + size(speed) + "/s";
    updateTask(task);
  }
  if (!task.bytes) throw new Error("Empty file");
  const blob = new Blob(chunks,{type:"application/vnd.android.package-archive"});
  const signature = new Uint8Array(await blob.slice(0,4).arrayBuffer());
  if (signature.join(",") !== "80,75,3,4") throw new Error("The source did not return an APK archive.");
  return blob;
}

async function download(id, { preferDrive = false, switchTab = true } = {}) {
  if (active.has(id)) { if (switchTab) location.hash = "downloads"; return; }
  const app = apps.find(entry => entry.id === id);
  if (!app || !downloadable(app)) return;
  if (!preferences.unknownApps) {
    requestUnknownAppsPermission(() => download(id, { preferDrive, switchTab }));
    return;
  }
  if (!wifiAllowed()) return;
  const directSource = preferDrive ? "" : direct(app);
  const proxySource = proxyDownloadUrl(app, preferDrive);
  const sources = directSource ? [directSource, proxySource] : [proxySource];
  downloadTab = "active";
  const controller = new AbortController();
  const task = {app, controller, index: ++taskIndex, bytes: 0, total: 0, status: "Connecting…"};
  active.set(id,task);
  if (route()[0] === "downloads") render();
  else if (switchTab) location.hash = "downloads";
  try {
    let blob;
    let lastError;
    for (const source of sources) {
      if (controller.signal.aborted) throw new DOMException("Aborted","AbortError");
      try {
        blob = await fetchApkStream(source, task, controller);
        break;
      } catch (error) {
        if (error.name === "AbortError") throw error;
        lastError = error;
        task.status = "Connecting…";
        updateTask(task);
      }
    }
    if (!blob) throw lastError || new Error("Download failed.");
    if (controller.signal.aborted) return;
    const fileName = apkFileName(app);
    if (files.size >= 5) {
      const oldestKey = files.keys().next().value;
      if (oldestKey) files.delete(oldestKey);
    }
    files.set(id, { blob, name: fileName });
    cacheApkBlob(id, fileName, blob);
    history[id] = { name: app.name, bytes: task.bytes, version: app.version || "", at: Date.now() };
    save("appnest-downloads", history);
    installed[id] = { name: app.name, version: app.version || "", at: Date.now() };
    save("appnest-installed", installed);
    saveFile(id);
    if (active.size === 1) downloadTab = "installed";
    notify(app.name + " downloaded and ready to open.");
    if (!globalThis.AppNestAndroid) {
      if (url(app.openUrl)) {
        launchExternalUrl(url(app.openUrl));
      } else {
        openSheet(
          '<div class="sheet-icon">' + ico.download + '</div>' +
          '<h2>' + esc(app.name) + ' is ready</h2>' +
          '<p>Download completed. Open the APK file now to install, or tap Done to open it later from any screen.</p>' +
          '<div class="sheet-actions">' +
          '<button type="button" class="primary" data-sheet-action="open-cached-app" data-app-id="' + esc(id) + '">Open APK</button>' +
          '<button type="button" class="subtle" data-sheet-close="1">Done</button>' +
          '</div>'
        );
      }
    }
  } catch (error) {
    if (error.name !== "AbortError") {
      toast(error.message || "Download failed. Check that the APK link is publicly accessible.");
    }
  } finally {
    active.delete(id);
    render();
  }
}

function watchCatalog() {
  catalogListener?.();
  state = "loading";
  render();
  catalogListener = api.onValue(api.ref(database,"apps"), snapshot => {
    apps = [];
    snapshot.forEach(child => {
      const value = child.val();
      if (value && typeof value.name === "string" && value.name.trim() && value.published !== false) {
        apps.push({...value, id:child.key, category:typeof value.category === "string" ? value.category.trim() : ""});
      }
    });
    if (!apps.some(app => app.category === category)) category = "";
    state = "ready";
    if (["home","search","app","downloads","updates","profile"].includes(route()[0])) render();
    checkUpdates();
  }, error => {
    apps = [];
    state = /permission/i.test(error.code + " " + error.message) ? "denied" : "error";
    if (["home","search","app","downloads","updates","profile"].includes(route()[0])) render();
  });
}

async function syncSignedInProfile(current) {
  if (!current) return;
  const fallbackName = (current.email || "").split("@")[0]
    .replace(/[._-]+/g, " ")
    .replace(/\b\w/g, c => c.toUpperCase())
    .trim();
  const resolvedName = (current.displayName || "").trim() || fallbackName;
  if (resolvedName) {
    profileMeta.guestName = resolvedName;
    if (!current.displayName && authApi?.updateProfile) {
      try { await authApi.updateProfile(current, { displayName: resolvedName }); } catch {}
    }
  }
  if (current.photoURL) {
    profileMeta.photoURL = current.photoURL;
  }
  save("appnest-profile", profileMeta);
}

async function connect() {
  state = loginState = "loading";
  render();
  try {
    const backend = await connectFirebase();
    ({db:api, authApi, database, auth} = backend);
    loginState = "ready";
    try {
      const redirectResult = await authApi.getRedirectResult(auth);
      if (redirectResult?.user) {
        await syncSignedInProfile(redirectResult.user);
        toast("Signed in as " + (redirectResult.user.displayName || redirectResult.user.email || "Google user") + ".");
      }
    } catch {}
    authApi.onAuthStateChanged(auth, async current => {
      user = current;
      if (current) await syncSignedInProfile(current);
      watchCatalog();
    });
    setTimeout(() => {
      if (state === "loading") { state = "error"; if (route()[0] !== "profile") render(); }
    },12000);
  } catch {
    state = loginState = "error";
    render();
  }
}

screen.addEventListener("click", async event => {
  const button = event.target.closest("button");
  if (!button) return;
  const data = button.dataset;
  if (data.category !== undefined) { category = data.category; render(); return; }
  if (data.sort) { sortMode = data.sort; render(); return; }
  if (data.searchSort) { searchSort = data.searchSort; const res = $("#results"); if (res) { res.innerHTML = searchResults(); button.closest(".sort-bar")?.querySelectorAll("button").forEach(b => b.setAttribute("aria-pressed", String(b === button))); } else render(); return; }
  if (data.recent !== undefined) { query = data.recent; render(); $("#search-form input")?.focus(); return; }
  if (data.openApp) { openApp(data.openApp); return; }
  if (data.download) { download(data.download); return; }
  if (data.drive) { download(data.drive, { preferDrive: true }); return; }
  if (data.saveToggle) { toggleSaved(data.saveToggle); render(); return; }
  if (data.avatarColor) {
    profileMeta.avatarColor = data.avatarColor;
    save("appnest-profile", profileMeta);
    render();
    toast("Avatar theme updated.");
    return;
  }
  if (data.star) {
    draftStar = Number(data.star);
    const picker = button.closest(".star-picker");
    if (picker) {
      [...picker.querySelectorAll(".star-btn")].forEach(b => {
        b.classList.toggle("filled", Number(b.dataset.star) <= draftStar);
      });
    }
    return;
  }
  if (data.deleteReview) {
    delete reviews[data.deleteReview];
    save("appnest-reviews", reviews);
    render();
    toast("Review removed.");
    return;
  }
  if (data.tab) { downloadTab = data.tab; render(); return; }
  if (data.pause) {
    const task = active.get(data.pause);
    if (!task) return;
    task.paused = !task.paused;
    if (!task.paused) { task.status = "Resuming…"; task.resume?.(); }
    else task.status = "Paused · " + size(task.bytes);
    render();
    return;
  }
  if (data.cancel) {
    const task = active.get(data.cancel);
    task?.controller.abort();
    task?.resume?.();
    return;
  }
  if (data.mark) {
    const entry = data.fromHistory ? history[data.mark] : apps.find(app => app.id === data.mark);
    if (!entry) return;
    installed[data.mark] = {name:entry.name,version:entry.version || ""};
    save("appnest-installed",installed);
    render();
    toast("Marked as installed on this device.");
    return;
  }
  if (data.unmark) {
    delete installed[data.unmark];
    save("appnest-installed",installed);
    render();
    return;
  }
  if (data.action === "toggle-edit") {
    profileEditing = !profileEditing;
    render();
    if (profileEditing) $("#edit-profile input[name='name']")?.focus();
    return;
  }
  if (data.action === "update-all") {
    const queue = availableUpdates().filter(downloadable);
    if (!queue.length) { toast("No downloadable updates found."); return; }
    const startUpdates = () => {
      location.hash = "downloads";
      for (const app of queue) download(app.id, { switchTab: false });
      toast("Queued " + queue.length + " app update(s).");
    };
    if (!preferences.unknownApps) {
      requestUnknownAppsPermission(startUpdates);
      return;
    }
    startUpdates();
    return;
  }
  if (data.action === "clear-history") {
    history = Object.create(null);
    files.clear();
    clearApkDb();
    save("appnest-downloads", {});
    render();
    toast("Download history cleared.");
    return;
  }
  if (data.action === "clear-searches") {
    recent = [];
    save("appnest-recent", []);
    render();
    toast("Recent searches cleared.");
    return;
  }
  if (data.action === "password") {
    const field = button.form.elements.password;
    field.type = field.type === "password" ? "text" : "password";
    button.textContent = field.type === "password" ? "Show password" : "Hide password";
    return;
  }
  if (data.action === "reset") {
    const field = button.form.elements.email;
    if (!field.reportValidity()) return;
    button.disabled = true;
    try {
      await authApi.sendPasswordResetEmail(auth,field.value.trim());
      toast("If an account exists for that email, check your inbox for a reset link.");
    } catch { toast("Couldn’t request a reset. Try again."); }
    finally { button.disabled = false; }
    return;
  }
  if (data.action === "google-signin") {
    if (!authApi || !auth) { toast("Firebase Authentication is not ready yet."); return; }
    button.disabled = true;
    try {
      const provider = new authApi.GoogleAuthProvider();
      provider.setCustomParameters({ prompt: "select_account" });
      const cred = await authApi.signInWithPopup(auth, provider);
      if (cred?.user) {
        await syncSignedInProfile(cred.user);
        render();
        toast("Signed in as " + (cred.user.displayName || cred.user.email || "Google user") + ".");
      }
    } catch (error) {
      if (error?.code === "auth/popup-blocked" || error?.code === "auth/operation-not-supported-in-this-environment") {
        try {
          const provider = new authApi.GoogleAuthProvider();
          await authApi.signInWithRedirect(auth, provider);
          return;
        } catch {}
      }
      const msgMap = {
        "auth/operation-not-allowed": "Enable Google sign-in in Firebase Authentication → Sign-in method.",
        "auth/unauthorized-domain": "Add this domain under Firebase Authentication → Settings → Authorized domains.",
        "auth/popup-closed-by-user": "Google sign-in cancelled."
      };
      toast(msgMap[error?.code] || "Google sign-in failed. Check Firebase Authentication settings.");
    } finally {
      button.disabled = false;
    }
    return;
  }
  if (data.action === "install-pwa") {
    triggerAppInstall();
    return;
  }
  if (data.action === "open-unknown-permission") {
    requestUnknownAppsPermission();
    return;
  }
  if (data.action === "open-storage") {
    openStorageSheet();
    return;
  }
  if (data.action === "open-about") {
    openAboutSheet();
    return;
  }
  if (data.save) { saveFile(data.save); return; }
  if (data.action === "retry") { location.reload(); return; }
  if (data.action === "mode") { accountMode = accountMode === "signin" ? "signup" : "signin"; render(); }
  if (data.action === "logout") {
    try {
      await authApi.signOut(auth);
      profileMeta.photoURL = "";
      save("appnest-profile", profileMeta);
      location.hash = "profile";
      toast("Signed out.");
    } catch { toast("Couldn’t sign out. Try again."); }
  }
});

sheetEl?.addEventListener("click", event => {
  if (event.target === sheetEl || event.target.closest("[data-sheet-close]")) {
    pendingPermissionAction = null;
    closeSheet();
    return;
  }
  const btn = event.target.closest("[data-sheet-action]");
  if (!btn) return;
  const action = btn.dataset.sheetAction;
  if (action === "grant-unknown") {
    preferences.unknownApps = true;
    save("appnest-preferences", preferences);
    const nextAction = pendingPermissionAction;
    pendingPermissionAction = null;
    closeSheet();
    render();
    toast("Unknown app downloads allowed.");
    nextAction?.();
  } else if (action === "deny-unknown") {
    preferences.unknownApps = false;
    save("appnest-preferences", preferences);
    pendingPermissionAction = null;
    closeSheet();
    render();
    toast("Unknown app downloads disabled.");
  } else if (action === "open-cached-app") {
    const targetId = btn.dataset.appId;
    closeSheet();
    if (targetId) openApp(targetId);
  } else if (action === "redownload-app") {
    const targetId = btn.dataset.appId;
    closeSheet();
    if (targetId) download(targetId);
  } else if (action === "clear-cache") {
    files.clear();
    clearApkDb();
    closeSheet();
    render();
    toast("Cached APK memory cleared.");
  } else if (action === "clear-history") {
    history = Object.create(null);
    files.clear();
    clearApkDb();
    save("appnest-downloads", {});
    closeSheet();
    render();
    toast("Download history cleared.");
  }
});

window.addEventListener("appnest:resume", () => render());

window.addEventListener("keydown", event => {
  if (event.key === "Escape" && sheetEl && !sheetEl.hidden) {
    pendingPermissionAction = null;
    closeSheet();
  }
});

screen.addEventListener("change", async event => {
  const field = event.target, key = field.dataset.preference;
  if (!key) return;
  if (key === "notifications" && field.checked) {
    try {
      field.checked = (globalThis.Notification?.permission === "granted" ||
        await globalThis.Notification?.requestPermission() === "granted");
    } catch { field.checked = false; }
    if (!field.checked) toast("Your browser didn’t grant notification access.");
    else notify("Notifications enabled for downloads and updates.");
  }
  preferences[key] = field.checked;
  save("appnest-preferences",preferences);
  if (key === "darkMode") {
    applyTheme(field.checked);
    toast(field.checked ? "Dark mode enabled." : "Light mode enabled.");
  }
  if (key === "wifi") {
    toast(field.checked ? "Wi-Fi-only download preference saved." : "Downloads allowed on any connection.");
  }
  if (key === "updates") {
    updateAlerted = false;
    checkUpdates();
    if (field.checked && !availableUpdates().length) {
      toast("Auto-update check enabled. All apps are up to date.");
    }
  }
});

screen.addEventListener("input", event => {
  if (event.target.form?.id !== "search-form") return;
  query = event.target.value;
  $("#results").innerHTML = searchResults();
});

screen.addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.target, values = new FormData(form);
  if (form.id === "search-form") {
    const term = query.trim();
    if (term) { recent = [term,...recent.filter(item => item.toLowerCase() !== term.toLowerCase())].slice(0,6); save("appnest-recent",recent); }
    render();
    $("#search-form input")?.focus();
    return;
  }
  if (form.id === "review-form") {
    const appId = form.dataset.appId;
    if (!appId) return;
    const text = String(values.get("reviewText") || "").trim();
    const selectedStars = form.querySelectorAll(".star-btn.filled").length;
    const rating = Math.min(5, Math.max(1, selectedStars || Number(draftStar) || 5));
    reviews[appId] = {
      rating,
      text,
      author: displayNameLabel() || "AppNest User",
      at: Date.now()
    };
    save("appnest-reviews", reviews);
    render();
    toast("Review saved.");
    return;
  }
  const button = form.querySelector("button");
  if (button) button.disabled = true;
  try {
    if (form.id === "edit-profile") {
      const name = String(values.get("name") || "").trim();
      const bio = String(values.get("bio") || "").trim();
      if (!name) { toast("Enter a display name."); return; }
      profileMeta.guestName = name;
      profileMeta.bio = bio;
      save("appnest-profile", profileMeta);
      if (user && authApi?.updateProfile) {
        await authApi.updateProfile(user, { displayName: name });
      }
      profileEditing = false;
      render();
      toast("Profile saved.");
    } else {
      const operation = accountMode === "signup" ? authApi.createUserWithEmailAndPassword : authApi.signInWithEmailAndPassword;
      await operation(auth,values.get("email").trim(),values.get("password"));
      toast("Signed in.");
    }
  } catch (error) {
    const messages = {"auth/operation-not-allowed":"Email/password sign-in is disabled in Firebase.",
      "auth/invalid-credential":"Check your email and password.","auth/email-already-in-use":"This email already has an account.",
      "auth/network-request-failed":"Couldn’t reach Firebase. Check your connection."};
    toast(messages[error.code] ?? "Couldn’t save your request. Try again.");
  } finally { if (button) button.disabled = false; }
});

$("#settings").addEventListener("click", () => { location.hash = "settings"; });
installBtn?.addEventListener("click", () => { triggerAppInstall(); });
window.addEventListener("hashchange", () => { pendingPermissionAction = null; closeSheet(); render(); window.scrollTo(0,0); screen.focus({preventScroll:true}); });
render();
connect();
