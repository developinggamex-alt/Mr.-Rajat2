export async function loadConfig() {
  const response = await fetch("/api/firebase-config");
  if (!response.ok) throw new Error("Unable to load Firebase configuration.");
  const config = await response.json();
  if (!config?.apiKey || !config?.databaseURL) {
    throw new Error("Firebase API key is not configured.");
  }
  return config;
}
export function https(value) {
  try { const url = new URL(value); return url.protocol === "https:" && !url.username && !url.password ? url : null; }
  catch { return null; }
}
export function driveId(value) {
  const input = https(value);
  if (!input) return null;
  const host = input.hostname.toLowerCase();
  let id = "";
  if (host === "drive.google.com" || host === "docs.google.com") {
    id = input.pathname.match(/^\/file\/d\/([\w-]+)(?:\/|$)/)?.[1] || input.searchParams.get("id") || "";
  } else if (host === "drive.usercontent.google.com") {
    id = input.searchParams.get("id") || "";
  } else {
    return null;
  }
  if (!/^[\w-]{10,128}$/.test(id)) return null;
  const key = input.searchParams.get("resourcekey");
  return { id, resourceKey: key && /^[\w-]{1,128}$/.test(key) ? key : "" };
}
export function drive(value) {
  const parsed = driveId(value);
  if (!parsed) return null;
  const target = new URL("https://drive.usercontent.google.com/download");
  target.searchParams.set("id", parsed.id);
  target.searchParams.set("export", "download");
  target.searchParams.set("confirm", "t");
  if (parsed.resourceKey) target.searchParams.set("resourcekey", parsed.resourceKey);
  return target.href;
}
export async function connectFirebase() {
  const config = await loadConfig();
  const sdk = "https://www.gstatic.com/firebasejs/11.10.0/";
  const [core, db, authApi] = await Promise.all([
    import(sdk + "firebase-app.js"), import(sdk + "firebase-database.js"), import(sdk + "firebase-auth.js")
  ]);
  const instance = core.getApps()[0] ?? core.initializeApp(config);
  return {db, authApi, database:db.getDatabase(instance), auth:authApi.getAuth(instance)};
}
