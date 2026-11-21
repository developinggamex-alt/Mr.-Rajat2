import {connectFirebase, drive, https} from "./firebase.mjs";

const byId = id => document.getElementById(id);
const editor = byId("editor"), status = byId("status"), dashboard = byId("dashboard");
const fields = ["name","category","developer","version","tagline","description","driveUrl","downloadUrl","iconUrl","bannerUrl","openUrl"];
const numbers = ["rating","reviewCount","downloadCount"];
let backend, account, selection, ready = false;
let published = {}, drafts = {};
let listeners = [];

function showRecords() {
  const list = byId("records");
  const entries = [...Object.entries(published).map(([id,app]) => [id,app,"Published"]),
    ...Object.entries(drafts).map(([id,app]) => [id,app,"Draft"])]
    .sort((a,b) => String(a[1].name).localeCompare(String(b[1].name)));
  list.replaceChildren();
  if (!entries.length) list.textContent = "No listings yet.";
  for (const [id, app, state] of entries) {
    const button = document.createElement("button");
    button.className = "subtle record";
    button.type = "button";
    button.textContent = (app.name || "Untitled app") + " · " + state;
    button.onclick = () => select(id,app);
    list.append(button);
  }
}
function select(id, app = {}) {
  selection = id;
  editor.reset();
  for (const name of [...fields,...numbers]) editor.elements[name].value = app[name] ?? "";
  editor.elements.sizeMb.value = app.sizeBytes ? app.sizeBytes / 1048576 : "";
  editor.elements.screenshots.value = Object.values(app.screenshots ?? {}).join("\n");
  editor.elements.featured.checked = app.featured === true;
  byId("editor-title").textContent = id ? "Edit listing" : "New listing";
  byId("editor-feedback").textContent = "";
}
function listing(form, publish) {
  const data = new FormData(form);
  const value = {};
  for (const name of fields) {
    const entry = data.get(name).trim();
    if (entry) value[name] = entry;
  }
  for (const name of ["driveUrl","downloadUrl","iconUrl","bannerUrl","openUrl"]) {
    if (!value[name]) continue;
    if (name === "driveUrl") {
      const normalized = drive(value[name]);
      if (!normalized) throw new Error("Enter a valid Google Drive file link.");
      value[name] = normalized;
    } else if (name === "downloadUrl") {
      const normalizedDrive = drive(value[name]);
      if (normalizedDrive) value[name] = normalizedDrive;
      else if (!https(value[name])) throw new Error("All image, app and download links must use HTTPS.");
    } else if (!https(value[name])) {
      throw new Error("All image, app and download links must use HTTPS.");
    }
  }
  const screenshots = data.get("screenshots").split(/\r?\n/).map(line => line.trim()).filter(Boolean);
  if (screenshots.length > 12 || screenshots.some(item => item.length > 2000 || !https(item))) throw new Error("Enter up to 12 valid HTTPS screenshot links.");
  if (screenshots.length) value.screenshots = screenshots;
  for (const name of [...numbers, "sizeMb"]) {
    const raw = data.get(name);
    if (!raw) continue;
    const number = Number(raw);
    if (!Number.isFinite(number) || number < 0 || (name === "rating" && number > 5) ||
      (["reviewCount","downloadCount"].includes(name) && !Number.isSafeInteger(number)) ||
      (name === "sizeMb" && !Number.isSafeInteger(Math.round(number * 1048576)))) throw new Error("Check the rating, counts and APK size.");
    value[name === "sizeMb" ? "sizeBytes" : name] = name === "sizeMb" ? Math.round(number * 1048576) : number;
  }
  if (publish && (!value.name || !value.category || !value.description || !(value.downloadUrl || value.driveUrl))) {
    throw new Error("Add a title, category, description and an APK link before publishing.");
  }
  return {...value, featured:data.has("featured"), published:publish, updatedAt:backend.db.serverTimestamp()};
}
byId("new").onclick = () => select(undefined);
byId("logout").onclick = async () => {
  try { await backend.authApi.signOut(backend.auth); } catch { status.textContent = "Couldn’t sign out. Try again."; }
};
byId("login").onsubmit = async event => {
  event.preventDefault();
  const form = event.currentTarget, data = new FormData(form), button = form.querySelector("button");
  button.disabled = true;
  try { await backend.authApi.signInWithEmailAndPassword(backend.auth, data.get("email").trim(),data.get("password")); form.reset(); }
  catch { status.textContent = "Sign-in failed. Check your credentials and enable Email/Password in Firebase."; }
  finally { button.disabled = false; }
};
editor.onsubmit = async event => {
  event.preventDefault();
  if (!account || !ready) return;
  const feedback = byId("editor-feedback");
  const publish = event.submitter?.value !== "draft";
  const buttons = [...editor.querySelectorAll("button")];
  buttons.forEach(button => button.disabled = true);
  try {
    const value = listing(editor,publish);
    const {db, database} = backend;
    const id = selection ?? db.push(db.ref(database,"apps")).key;
    await db.update(db.ref(database), {["apps/"+id]:publish ? value : null, ["appDrafts/"+id]:publish ? null : value});
    selection = id;
    byId("editor-title").textContent = "Edit listing";
    feedback.textContent = publish ? "Published to the user panel." : "Saved as a private draft and removed from the public catalog.";
  } catch (error) {
    feedback.textContent = /permission/i.test(error.code ?? "") ? "Firebase denied this write. Check the admin role and rules." : error.message;
  } finally { buttons.forEach(button => button.disabled = false); }
};
try {
  backend = await connectFirebase();
  const {auth,authApi,db,database} = backend;
  authApi.onAuthStateChanged(auth, user => {
    listeners.forEach(stop => stop());
    listeners = [];
    account = user;
    ready = false;
    published = {};
    drafts = {};
    dashboard.hidden = true;
    byId("gate").hidden = !!user;
    byId("logout").hidden = !user;
    status.textContent = user ? "Checking admin access…" : "Sign in with your Firebase administrator account.";
    if (!user) return;
    let catalogStops = [];
    listeners.push(db.onValue(db.ref(database,"admins/"+user.uid), snapshot => {
      catalogStops.forEach(stop => stop());
      catalogStops = [];
      ready = snapshot.val() === true;
      dashboard.hidden = !ready;
      if (!ready) {
        status.textContent = "Admin access required. Your Firebase UID is " + user.uid;
        return;
      }
      status.textContent = "Publish apps and games to your AppNest catalog.";
      select(undefined);
      const failed = () => { status.textContent = "Catalog access failed. Check your Firebase rules."; };
      catalogStops.push(db.onValue(db.ref(database,"apps"), data => { published = data.val() ?? {}; showRecords(); }, failed));
      catalogStops.push(db.onValue(db.ref(database,"appDrafts"), data => { drafts = data.val() ?? {}; showRecords(); }, failed));
    }, () => { status.textContent = "Couldn’t verify admin access. Check the Firebase rules."; }));
    listeners.push(() => catalogStops.forEach(stop => stop()));
  });
} catch { status.textContent = "Couldn’t connect to Firebase. Check your network and reload."; }
