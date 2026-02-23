#!/usr/bin/env node

const blessed = require("blessed");
const fs = require("fs");
const path = require("path");

const API_KEY = process.env.TRAIN_API_KEY;
if (!API_KEY) {
  console.error("TRAIN_API_KEY manquant. Export la variable : export TRAIN_API_KEY=ta_cle");
  process.exit(1);
}
const BASE_URL = "https://prim.iledefrance-mobilites.fr/marketplace/v2/navitia";
const SEARCH_URL = `${BASE_URL}/places`;
const JOURNEYS_URL = `${BASE_URL}/journeys`;
const REFRESH_INTERVAL = 600000; // 10 min
const CONFIG_PATH = path.join(__dirname, "config.json");

// --- Config ---

const DEFAULTS = {
  from: { id: "stop_area:IDFM:63498", name: "La Ferté-sous-Jouarre" },
  to: { id: "stop_area:IDFM:474151", name: "Châtelet les Halles" },
};

function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf-8"));
  } catch {
    return DEFAULTS;
  }
}

function saveConfig() {
  fs.writeFileSync(CONFIG_PATH, JSON.stringify({
    from: fromStation,
    to: toStation,
    display,
    columns,
    routeMode,
    walkDeparture,
    walkArrival,
    favorites,
  }, null, 2));
}

// --- State ---

const config = loadConfig();
let fromStation = config.from;
let toStation = config.to;
let departures = [];

// Display toggles
const display = {
  banner: true,
  tableHeader: true,
  aligned: true,
  ...(config.display || {}),
};

// Column visibility toggles
const columns = {
  wait: true,
  departure: true,
  arrival: true,
  duration: true,
  ...(config.columns || {}),
};

// Route display: "off" | "code" | "full"
let routeMode = config.routeMode || "full";

// Walk times (minutes)
let walkDeparture = config.walkDeparture || config.walkMinutes || 0;
let walkArrival = config.walkArrival || 0;

// Favorites
let favorites = config.favorites || [];

// --- API ---

async function searchStations(query) {
  const url = `${SEARCH_URL}?q=${encodeURIComponent(query)}&type[]=stop_area&count=15`;
  const res = await fetch(url, { headers: { apiKey: API_KEY } });
  if (!res.ok) return [];
  const json = await res.json();
  const places = json.places || [];
  return places
    .filter((p) => p.embedded_type === "stop_area")
    .map((p) => {
      const sa = p.stop_area;
      const modes = (sa.commercial_modes || []).map((m) => m.name).join(", ");
      const region = (sa.administrative_regions || []).find((r) => r.level === 8);
      const city = region?.label || "";
      return {
        id: sa.id, // stop_area:IDFM:XXXXX (navitia format)
        name: sa.name,
        modes,
        city,
      };
    });
}

function parseNavitiaTime(str) {
  // 20260220T183000 → Date object (Navitia returns local Paris time)
  if (!str) return null;
  const y = str.slice(0, 4), m = str.slice(4, 6), d = str.slice(6, 8);
  const h = str.slice(9, 11), mi = str.slice(11, 13), s = str.slice(13, 15);
  // Keep as local time string — don't convert to UTC
  return `${y}-${m}-${d}T${h}:${mi}:${s}`;
}

async function fetchJourneys() {
  const now = new Date();
  const dt = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}T${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const from = fromStation.id;
  const to = toStation.id;
  const url = `${JOURNEYS_URL}?from=${from}&to=${to}&datetime=${dt}&count=8&min_nb_journeys=5`;

  const res = await fetch(url, { headers: { apiKey: API_KEY } });
  if (!res.ok) throw new Error(`API error: ${res.status}`);
  const json = await res.json();

  return (json.journeys || []).map((j) => {
    const allSections = j.sections || [];
    const steps = [];
    let pendingWalk = 0;

    for (const s of allSections) {
      if (s.type === "public_transport") {
        const info = s.display_informations || {};
        steps.push({
          mode: info.commercial_mode || "?",
          code: info.code || "",
          direction: info.direction || "",
          from: s.from?.stop_point?.name || "?",
          to: s.to?.stop_point?.name || "?",
          duration: s.duration || 0,
          walkBefore: pendingWalk,
        });
        pendingWalk = 0;
      } else {
        pendingWalk += s.duration || 0;
      }
    }

    return {
      departure: parseNavitiaTime(j.departure_date_time),
      arrivalAtDest: parseNavitiaTime(j.arrival_date_time),
      duration: j.duration,
      transfers: j.nb_transfers,
      steps,
      walkAfterLast: pendingWalk,
      status: j.status === "NO_SERVICE" ? "cancelled" : "onTime",
      // Display info from first transport section
      code: steps[0]?.code || "",
      dest: steps.length === 1
        ? steps[0].direction
        : `${steps.length} corresp.`,
      platform: "",
    };
  });
}

// --- Helpers ---

function pad(n) {
  return n.toString().padStart(2, "0");
}

function fmtClock() {
  const now = new Date();
  return `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

function fmtTime(isoStr) {
  const d = new Date(isoStr);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function updateProcessTitle() {
  const next = departures.find((d) => d.status !== "cancelled" && d.arrivalAtDest);
  if (!next) { process.title = "--:--"; return; }
  const arrDate = new Date(new Date(next.arrivalAtDest).getTime() + walkArrival * 60000);
  process.title = fmtTime(arrDate.toISOString());
}

function fmtWait(dep) {
  if (dep.status === "cancelled") return "{red-fg}Annulé{/red-fg}";
  const diffMin = Math.round((new Date(dep.departure) - new Date()) / 60000);
  if (diffMin <= 0) return "{yellow-fg}à quai{/yellow-fg}";
  if (walkDeparture > 0 && diffMin <= walkDeparture) {
    return `{magenta-fg}${diffMin} min{/magenta-fg}`;
  }
  if (diffMin === 1) return "{green-fg}1 min{/green-fg}";
  return `{green-fg}${diffMin} min{/green-fg}`;
}


// --- TUI ---

function createUI() {
  const screen = blessed.screen({
    smartCSR: true,
    title: `${fromStation.name} → ${toStation.name}`,
    fullUnicode: true,
  });

  // Header
  const header = blessed.box({
    parent: screen,
    top: 0,
    left: 0,
    width: "100%",
    height: 3,
    tags: true,
    style: { bg: "red", fg: "white", bold: true },
  });

  // Table
  const table = blessed.box({
    parent: screen,
    top: 3,
    left: 0,
    width: "100%",
    bottom: 3,
    tags: true,
    border: { type: "line" },
    style: { border: { fg: "gray" }, fg: "white" },
    scrollable: true,
    keys: true,
    vi: true,
    label: " {bold}Prochains départs{/bold} ",
    padding: { left: 1, right: 1 },
  });

  // Footer
  const footer = blessed.box({
    parent: screen,
    bottom: 0,
    left: 0,
    width: "100%",
    height: 3,
    tags: true,
    style: { bg: "black", fg: "gray" },
  });

  function updateHeader() {
    const clock = fmtClock();
    const title = ` {bold}${fromStation.name}{/bold} → ${toStation.name}`;
    const right = `${clock}  `;
    const space = Math.max(0, (screen.width || 60) - blessed.stripTags(title).length - right.length);
    header.setContent(`\n${title}${" ".repeat(space)}${right}`);
  }

  function updateFooter(count, lastUpdate) {
    const left = ` {bold}q{/bold} Quitter  {bold}r{/bold} Rafraîchir  {bold}d{/bold} Départ  {bold}a{/bold} Arrivée`;
    const right = `${count} trains  màj ${lastUpdate} `;
    const space = Math.max(0, (screen.width || 60) - blessed.stripTags(left).length - blessed.stripTags(right).length);
    footer.setContent(`\n${left}${" ".repeat(space)}${right}`);
  }

  // Clock (visible when banner is hidden)
  const compactClock = blessed.box({
    parent: screen,
    top: 0,
    right: 1,
    width: 10,
    height: 1,
    tags: true,
    style: { fg: "gray" },
  });
  compactClock.hide();

  function updateCompactClock() {
    compactClock.setContent(`${fmtClock()}`);
  }

  // Responsive
  function applyLayout() {
    const compact = screen.height < 10 || screen.width < 50;
    const showBanner = !compact && display.banner;

    if (compact) {
      header.hide();
      footer.hide();
      compactClock.show();
      compactClock.top = 0;
      compactClock.right = 0;
      table.top = 0;
      table.bottom = 0;
      table.border = null;
      table.label = "";
      table.padding = { left: 0, right: 0 };
    } else {
      if (showBanner) {
        header.show();
        compactClock.hide();
        table.top = 3;
      } else {
        header.hide();
        compactClock.show();
        // Align clock with first data row inside the table (border=1 + padding=0)
        compactClock.top = 1;
        compactClock.right = 3;
      }
      footer.show();
      table.bottom = 3;
      table.top = showBanner ? 3 : 0;
      table.border = { type: "line" };
      table.label = " {bold}Prochains départs{/bold} ";
      table.padding = { left: 1, right: 1 };
    }
  }

  screen.on("resize", () => {
    applyLayout();
    if (departures.length > 0) renderTable(table, departures, screen);
    screen.render();
  });

  applyLayout();

  let pickerOpen = false;

  screen.key(["q", "C-c", "escape"], () => {
    if (!pickerOpen) process.exit(0);
  });

  return { screen, header, table, footer, updateHeader, updateFooter, updateCompactClock, applyLayout, isCompact: () => screen.height < 10 || screen.width < 50, setPickerOpen: (v) => { pickerOpen = v; }, getPickerOpen: () => pickerOpen };
}

function fmtSteps(d, firstWalkPad) {
  if (routeMode === "off") return "";
  const { steps } = d;
  const cancelled = d.status === "cancelled";
  const segments = [];
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    const code = cancelled ? `{red-fg}${s.code}{/red-fg}` : s.code;
    const mode = routeMode === "full" ? `{gray-fg}${s.mode}{/gray-fg} ` : "";
    const dur = s.duration ? ` {blue-fg}${Math.round(s.duration / 60)}{/blue-fg}` : "";
    const walkMin = Math.round(s.walkBefore / 60);
    let walk;
    if (i === 0) {
      const pad = firstWalkPad || 0;
      if (walkMin > 0) {
        walk = `{gray-fg}${walkMin.toString().padStart(pad)}{/gray-fg} `;
      } else if (pad > 0) {
        walk = " ".repeat(pad) + " ";
      } else {
        walk = "";
      }
    } else {
      walk = walkMin > 0 ? `|{gray-fg}${walkMin}{/gray-fg} ` : " ";
    }
    segments.push(`${walk}${mode}${code}${dur}`);
  }
  const walkAfter = Math.round((d.walkAfterLast || 0) / 60);
  if (walkAfter > 0) segments.push(` {gray-fg}${walkAfter}{/gray-fg}`);
  return segments.join("");
}

function buildRowParts(d, firstWalkPad) {
  const parts = [];

  if (columns.wait) parts.push(fmtWait(d));
  if (columns.departure) {
    const depDate = new Date(new Date(d.departure).getTime() - walkDeparture * 60000);
    const walkTag = walkDeparture > 0 ? `{gray-fg}${walkDeparture}{/gray-fg} ` : "";
    parts.push(`${walkTag}${fmtTime(depDate.toISOString())}`);
  }
  if (columns.arrival) {
    if (d.arrivalAtDest) {
      const arrDate = new Date(new Date(d.arrivalAtDest).getTime() + walkArrival * 60000);
      const walkTag = walkArrival > 0 ? `{gray-fg}${walkArrival}{/gray-fg} ` : "";
      parts.push(`→ ${walkTag}${fmtTime(arrDate.toISOString())}`);
    } else {
      parts.push("→ --:--");
    }
  }
  if (columns.duration) {
    let totalSec = walkDeparture * 60 + walkArrival * 60 + (d.walkAfterLast || 0);
    for (const s of d.steps) {
      totalSec += (s.duration || 0) + (s.walkBefore || 0);
    }
    parts.push(`{magenta-fg}${Math.round(totalSec / 60)}{/magenta-fg}`);
  }
  if (routeMode !== "off") parts.push(fmtSteps(d, firstWalkPad));

  return parts;
}

function buildHeaderParts() {
  const parts = [];
  if (columns.wait) parts.push("Attente");
  if (columns.departure) parts.push("Départ");
  if (columns.arrival) parts.push("Arrivée");
  if (columns.duration) parts.push("Total");
  if (routeMode !== "off") parts.push("Trajet");
  return parts;
}

function padRow(parts, widths) {
  return parts.map((p, i) => {
    const visual = blessed.stripTags(p).length;
    const pad = Math.max(0, widths[i] - visual);
    return p + " ".repeat(pad);
  }).join("  ");
}

function renderTable(table, deps, screen) {
  if (deps.length === 0) {
    table.setContent("\n {gray-fg}Aucun trajet à venir{/gray-fg}");
    return;
  }

  const compact = screen.height < 10 || screen.width < 50;
  const visible = deps.slice(0, 15);

  // Compute max first-step walk width for route alignment
  let firstWalkPad = 0;
  if (routeMode !== "off") {
    for (const d of visible) {
      if (d.steps.length > 0) {
        const w = Math.round(d.steps[0].walkBefore / 60);
        if (w > 0) firstWalkPad = Math.max(firstWalkPad, w.toString().length);
      }
    }
  }

  const allParts = visible.map((d) => buildRowParts(d, firstWalkPad));

  // Compute max widths per column
  const widths = [];
  if (display.aligned) {
    const headerParts = buildHeaderParts();
    for (let i = 0; i < headerParts.length; i++) {
      widths[i] = headerParts[i].length;
    }
    for (const parts of allParts) {
      for (let i = 0; i < parts.length; i++) {
        const w = blessed.stripTags(parts[i]).length;
        widths[i] = Math.max(widths[i] || 0, w);
      }
    }
  }

  const rows = allParts.map((parts) => {
    const prefix = compact ? "" : " ";
    if (display.aligned) return prefix + padRow(parts, widths);
    return prefix + parts.join("  ");
  });

  if (compact) {
    table.setContent(rows.join("\n"));
  } else if (display.tableHeader) {
    const headerParts = buildHeaderParts();
    const headerStr = display.aligned
      ? " {bold}{underline}" + padRow(headerParts, widths) + "{/underline}{/bold}"
      : " {bold}{underline}" + headerParts.join("  ") + "{/underline}{/bold}";
    table.setContent(`\n${headerStr}\n\n${rows.join("\n")}`);
  } else {
    table.setContent(rows.join("\n"));
  }
}

// --- Station picker ---

function showStationPicker(screen, label, compact, onDone) {
  let results = [];
  let searchTimeout = null;
  let query = "";
  let selectedIndex = 0;
  let focusOn = "input";

  // Compact: full screen, no borders. Normal: centered popup.
  const container = blessed.box({
    parent: screen,
    top: compact ? 0 : "center",
    left: compact ? 0 : "center",
    width: compact ? "100%" : "65%",
    height: compact ? "100%" : "75%",
    border: compact ? null : { type: "line" },
    style: { border: { fg: "cyan" }, bg: "black" },
    tags: true,
    label: compact ? "" : ` {bold}${label}{/bold} `,
    keyable: true,
  });

  const inputBox = blessed.box({
    parent: container,
    top: 0,
    left: compact ? 0 : 1,
    right: compact ? 0 : 1,
    height: compact ? 1 : 3,
    border: compact ? null : { type: "line" },
    style: { border: { fg: "cyan" }, fg: "white", bg: "black" },
    tags: true,
    label: compact ? "" : ` ${label} `,
    content: "",
  });

  const list = blessed.list({
    parent: container,
    top: compact ? 1 : 4,
    left: compact ? 0 : 1,
    right: compact ? 0 : 1,
    bottom: compact ? 0 : 1,
    tags: true,
    style: {
      fg: "white",
      bg: "black",
      selected: { bg: "red", fg: "white", bold: true },
      item: { fg: "white", bg: "black" },
    },
    mouse: true,
    scrollable: true,
    items: compact ? [] : [" {gray-fg}Tapez pour rechercher...{/gray-fg}"],
  });

  function updateInput() {
    const cursor = focusOn === "input" ? "{underline} {/underline}" : "";
    const prefix = compact ? "" : " ";
    inputBox.setContent(`${prefix}${query}${cursor}`);
    if (!compact) inputBox.style.border.fg = focusOn === "input" ? "cyan" : "white";
  }

  function updateList() {
    if (results.length > 0) list.select(selectedIndex);
  }

  function cleanup() {
    if (searchTimeout) clearTimeout(searchTimeout);
    container.destroy();
    screen.render();
  }

  async function doSearch() {
    if (query.length < 2) {
      results = [];
      selectedIndex = 0;
      list.setItems(compact ? [] : [" {gray-fg}Tapez au moins 2 caractères...{/gray-fg}"]);
      screen.render();
      return;
    }

    list.setItems([compact ? "..." : " {gray-fg}Recherche...{/gray-fg}"]);
    screen.render();

    try {
      results = await searchStations(query);
      selectedIndex = 0;
      if (results.length === 0) {
        list.setItems([compact ? "Aucun résultat" : " {gray-fg}Aucun résultat{/gray-fg}"]);
      } else {
        list.setItems(results.map((s) => {
          const mode = s.modes ? ` {gray-fg}${s.modes}{/gray-fg}` : "";
          const city = s.city ? ` {blue-fg}${s.city}{/blue-fg}` : "";
          return ` ${s.name}${mode}${city}`;
        }));
        list.select(0);
      }
    } catch {
      list.setItems([" {red-fg}Erreur{/red-fg}"]);
    }
    container.focus();
    updateInput();
    screen.render();
  }

  container.focus();

  container.on("keypress", (ch, key) => {
    if (key.name === "escape") {
      cleanup();
      onDone(null);
      return;
    }

    if (key.name === "return" || key.name === "enter") {
      if (focusOn === "list" && results[selectedIndex]) {
        cleanup();
        onDone(results[selectedIndex]);
      } else if (focusOn === "input" && results.length > 0) {
        focusOn = "list";
        selectedIndex = 0;
        updateInput();
        updateList();
        screen.render();
      }
      return;
    }

    if (key.name === "tab") {
      focusOn = focusOn === "input" ? "list" : "input";
      if (focusOn === "list" && results.length === 0) focusOn = "input";
      updateInput();
      screen.render();
      return;
    }

    if (focusOn === "list") {
      if (key.name === "down" || key.name === "j") {
        selectedIndex = Math.min(selectedIndex + 1, results.length - 1);
        updateList();
        screen.render();
      } else if (key.name === "up" || key.name === "k") {
        if (selectedIndex === 0) {
          focusOn = "input";
          updateInput();
          screen.render();
        } else {
          selectedIndex = Math.max(selectedIndex - 1, 0);
          updateList();
          screen.render();
        }
      }
      return;
    }

    // Input mode
    if (key.name === "down") {
      if (results.length > 0) {
        focusOn = "list";
        selectedIndex = 0;
        updateInput();
        updateList();
        screen.render();
      }
      return;
    }

    if (key.name === "backspace") {
      query = query.slice(0, -1);
      updateInput();
      screen.render();
    } else if (ch && !key.ctrl && !key.meta && ch.length === 1) {
      query += ch;
      updateInput();
      screen.render();
    }

    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(doSearch, 400);
  });

  list.on("click", () => {
    focusOn = "list";
    selectedIndex = list.selected || 0;
    if (results[selectedIndex]) {
      cleanup();
      onDone(results[selectedIndex]);
    }
  });

  updateInput();
  screen.render();
}

// --- Favorites panel ---

function showFavoritesPanel(screen, compact, onAction) {
  let selectedIndex = 0;

  const container = blessed.box({
    parent: screen,
    top: compact ? 0 : "center",
    left: compact ? 0 : "center",
    width: compact ? "100%" : "65%",
    height: compact ? "100%" : "75%",
    border: compact ? null : { type: "line" },
    style: { border: { fg: "cyan" }, bg: "black" },
    tags: true,
    label: compact ? "" : " {bold}Favoris{/bold} ",
    keyable: true,
  });

  const list = blessed.list({
    parent: container,
    top: compact ? 0 : 1,
    left: compact ? 0 : 1,
    right: compact ? 0 : 1,
    bottom: compact ? 0 : 1,
    tags: true,
    style: {
      fg: "white",
      bg: "black",
      selected: { bg: "red", fg: "white", bold: true },
      item: { fg: "white", bg: "black" },
    },
    scrollable: true,
  });

  function refreshList() {
    if (favorites.length === 0) {
      list.setItems([compact ? "Aucun favori — F pour ajouter" : " {gray-fg}Aucun favori — F pour ajouter{/gray-fg}"]);
    } else {
      list.setItems(favorites.map((f) => ` ${f.from.name} → ${f.to.name}`));
      selectedIndex = Math.min(selectedIndex, favorites.length - 1);
      list.select(selectedIndex);
    }
    screen.render();
  }

  function cleanup() {
    container.destroy();
    screen.render();
  }

  container.focus();

  container.on("keypress", (_ch, key) => {
    if (key.name === "escape") {
      cleanup();
      onAction(null);
      return;
    }

    if (favorites.length === 0) return;

    if (key.name === "down" || key.name === "j") {
      selectedIndex = Math.min(selectedIndex + 1, favorites.length - 1);
      list.select(selectedIndex);
      screen.render();
      return;
    }
    if (key.name === "up" || key.name === "k") {
      selectedIndex = Math.max(selectedIndex - 1, 0);
      list.select(selectedIndex);
      screen.render();
      return;
    }

    if (key.name === "return" || key.name === "enter") {
      const fav = favorites[selectedIndex];
      cleanup();
      onAction({ type: "load", favorite: fav });
      return;
    }

    if (key.name === "d" || key.name === "x") {
      favorites.splice(selectedIndex, 1);
      saveConfig();
      if (favorites.length === 0) {
        selectedIndex = 0;
      } else {
        selectedIndex = Math.min(selectedIndex, favorites.length - 1);
      }
      refreshList();
      return;
    }
  });

  refreshList();
}

// --- Main ---

async function main() {
  const { screen, table, updateHeader, updateFooter, updateCompactClock, applyLayout, isCompact, setPickerOpen, getPickerOpen } = createUI();

  let refreshTimer = null;

  function scheduleRefresh() {
    if (refreshTimer) clearTimeout(refreshTimer);

    if (departures.length === 0) {
      refreshTimer = setTimeout(refresh, REFRESH_INTERVAL);
      return;
    }

    // Refresh when the last visible train departs
    const lastDeparture = departures[departures.length - 1];
    const lastTime = new Date(lastDeparture.departure).getTime();
    const delay = Math.max(lastTime - Date.now() + 5000, 10000); // +5s margin, min 10s
    refreshTimer = setTimeout(refresh, delay);
  }

  async function refresh() {
    try {
      departures = await fetchJourneys();
      departures.sort((a, b) => new Date(a.departure) - new Date(b.departure));
      departures = departures.filter((d) => new Date(d.departure) > new Date());
      renderTable(table, departures, screen);
      updateFooter(departures.length, fmtClock());
      updateProcessTitle();
      scheduleRefresh();
    } catch (err) {
      table.setContent(`\n {red-fg}Erreur: ${err.message}{/red-fg}`);
      refreshTimer = setTimeout(refresh, REFRESH_INTERVAL);
    }
    screen.render();
  }

  // Help overlay
  let helpOpen = false;
  let helpBox = null;

  function toggleHelp() {
    if (helpOpen && helpBox) {
      helpBox.destroy();
      helpBox = null;
      helpOpen = false;
      screen.render();
      return;
    }

    const helpWidth = Math.min(50, Math.max(36, screen.width - 6));
    const helpHeight = Math.min(24, screen.height - 2);
    helpBox = blessed.box({
      parent: screen,
      top: "center",
      left: "center",
      width: helpWidth,
      height: helpHeight,
      tags: true,
      border: { type: "line" },
      style: { border: { fg: "cyan" }, bg: "black", fg: "white" },
      label: " {bold}Aide{/bold} ",
      scrollable: true,
      keys: true,
      vi: true,
      scrollbar: { style: { bg: "cyan" } },
    });

    refreshHelp();

    helpOpen = true;
    screen.render();
  }

  function rerender() {
    applyLayout();
    renderTable(table, departures, screen);
    if (helpOpen) refreshHelp();
    saveConfig();
    screen.render();
  }

  function refreshHelp() {
    if (!helpBox) return;
    const on = (v) => v ? "{green-fg}●{/green-fg}" : "{gray-fg}○{/gray-fg}";
    const routeLabel = { off: "{gray-fg}○{/gray-fg}", code: "{yellow-fg}◐{/yellow-fg}", full: "{green-fg}●{/green-fg}" };
    helpBox.setContent([
      "",
      "  {bold}Lecture{/bold}",
      "",
      "  {green-fg}3 min{/green-fg}     temps avant le départ",
      "  {yellow-fg}à quai{/yellow-fg}    train en gare",
      "  {magenta-fg}2 min{/magenta-fg}     pas le temps d'y aller",
      "  {red-fg}Annulé{/red-fg}    train supprimé",
      "",
      "  {gray-fg}5{/gray-fg} 15:19    {gray-fg}marche{/gray-fg} puis heure de départ",
      "  → {gray-fg}10{/gray-fg} 16:24  {gray-fg}marche{/gray-fg} puis heure d'arrivée",
      "  {magenta-fg}66{/magenta-fg}          durée totale porte à porte",
      "",
      "  {gray-fg}3{/gray-fg} A {blue-fg}6{/blue-fg}|{gray-fg}9{/gray-fg} E {blue-fg}22{/blue-fg}|{gray-fg}16{/gray-fg} J {blue-fg}14{/blue-fg} {gray-fg}2{/gray-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}     {blue-fg}│{/blue-fg}  {gray-fg}│{/gray-fg}    {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}     {blue-fg}│{/blue-fg}  {gray-fg}│{/gray-fg}    {blue-fg}│{/blue-fg} {gray-fg}└ marche fin{/gray-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}     {blue-fg}│{/blue-fg}  {gray-fg}│{/gray-fg}    {blue-fg}└ transport{/blue-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}     {blue-fg}│{/blue-fg}  {gray-fg}└ corresp.{/gray-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}│{/gray-fg}     {blue-fg}└ transport{/blue-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}│{/blue-fg} {gray-fg}└ corresp.{/gray-fg}",
      "  {gray-fg}│{/gray-fg}   {blue-fg}└ transport{/blue-fg}",
      "  {gray-fg}└ marche début{/gray-fg}",
      "",
      "  {bold}Colonnes{/bold}           {bold}Affichage{/bold}",
      `  {bold}W{/bold} Attente     ${on(columns.wait)}   {bold}A{/bold} Aligner   ${on(display.aligned)}`,
      `  {bold}D{/bold} Départ      ${on(columns.departure)}   {bold}B{/bold} Bandeau   ${on(display.banner)}`,
      `  {bold}R{/bold} Arrivée     ${on(columns.arrival)}   {bold}H{/bold} En-tête   ${on(display.tableHeader)}`,
      `  {bold}U{/bold} Durée       ${on(columns.duration)}   {bold}T{/bold} Transport ${routeLabel[routeMode]}`,
      "",
      `  {bold}+{/bold}/{bold}-{/bold} Marche départ       ${walkDeparture > 0 ? `{magenta-fg}${walkDeparture}{/magenta-fg}` : "{gray-fg}0{/gray-fg}"}`,
      `  {bold}]{/bold}/{bold}[{/bold} Marche arrivée      ${walkArrival > 0 ? `{cyan-fg}${walkArrival}{/cyan-fg}` : "{gray-fg}0{/gray-fg}"}`,
      "",
      "  {bold}Actions{/bold}",
      "  {bold}r{/bold} Rafraîchir     {bold}d{/bold} Départ",
      "  {bold}a{/bold} Arrivée        {bold}q{/bold} Quitter",
      "  {bold}f{/bold} Favoris        {bold}F{/bold} Ajouter favori",
      "  {bold}?{/bold} Fermer l'aide",
      "",
    ].join("\n"));
  }

  // Column toggles
  function toggleColumn(key) {
    if (getPickerOpen()) return;
    columns[key] = !columns[key];
    rerender();
  }

  screen.key(["S-w"], () => toggleColumn("wait"));
  screen.key(["S-d"], () => toggleColumn("departure"));
  screen.key(["S-r"], () => toggleColumn("arrival"));
  screen.key(["S-u"], () => toggleColumn("duration"));

  // Display toggles (MAJUSCULE)
  screen.key(["S-b"], () => {
    if (getPickerOpen()) return;
    display.banner = !display.banner;
    rerender();
  });
  screen.key(["S-a"], () => {
    if (getPickerOpen()) return;
    display.aligned = !display.aligned;
    rerender();
  });
  screen.key(["S-h"], () => {
    if (getPickerOpen()) return;
    display.tableHeader = !display.tableHeader;
    rerender();
  });
  screen.key(["S-t"], () => {
    if (getPickerOpen()) return;
    const cycle = { off: "code", code: "full", full: "off" };
    routeMode = cycle[routeMode];
    rerender();
  });

  // Walk times
  screen.key(["+", "="], () => {
    if (getPickerOpen()) return;
    walkDeparture++;
    rerender();
  });
  screen.key(["-"], () => {
    if (getPickerOpen()) return;
    if (walkDeparture > 0) walkDeparture--;
    rerender();
  });
  screen.key(["]"], () => {
    if (getPickerOpen()) return;
    walkArrival++;
    rerender();
  });
  screen.key(["["], () => {
    if (getPickerOpen()) return;
    if (walkArrival > 0) walkArrival--;
    rerender();
  });

  screen.key(["?"], () => { if (!getPickerOpen()) toggleHelp(); });

  // Keybindings
  screen.key(["r"], () => { if (!getPickerOpen()) refresh(); });

  function openPicker(label, onSelect) {
    setPickerOpen(true);
    showStationPicker(screen, label, isCompact(), async (station) => {
      setPickerOpen(false);
      if (station) {
        onSelect(station);
        saveConfig();
        screen.title = `${fromStation.name} → ${toStation.name}`;
        updateHeader();
        await refresh();
      }
    });
  }

  screen.key(["d"], () => {
    if (getPickerOpen()) return;
    openPicker("Gare de départ", (s) => { fromStation = s; });
  });

  screen.key(["a"], () => {
    if (getPickerOpen()) return;
    openPicker("Gare d'arrivée", (s) => { toStation = s; });
  });

  // Favorites
  screen.key(["f"], () => {
    if (getPickerOpen()) return;
    setPickerOpen(true);
    showFavoritesPanel(screen, isCompact(), async (action) => {
      setPickerOpen(false);
      if (action && action.type === "load") {
        fromStation = action.favorite.from;
        toStation = action.favorite.to;
        saveConfig();
        screen.title = `${fromStation.name} → ${toStation.name}`;
        updateHeader();
        await refresh();
      }
    });
  });

  screen.key(["S-f"], () => {
    if (getPickerOpen()) return;
    const exists = favorites.some(
      (f) => f.from.id === fromStation.id && f.to.id === toStation.id
    );
    if (!exists) {
      favorites.push({ from: { ...fromStation }, to: { ...toStation } });
      saveConfig();
    }
    // Temporary feedback in footer
    const origContent = footer.getContent();
    footer.setContent(`\n {green-fg}${exists ? "Déjà en favoris" : "Favori ajouté !"}{/green-fg}  ${fromStation.name} → ${toStation.name}`);
    screen.render();
    setTimeout(() => {
      updateFooter(departures.length, fmtClock());
      screen.render();
    }, 2000);
  });

  // Clock + live wait time update
  setInterval(() => {
    updateHeader();
    updateCompactClock();
    if (departures.length > 0) {
      // Filter out departed trains
      departures = departures.filter((d) => new Date(d.departure) > new Date());
      renderTable(table, departures, screen);
      updateProcessTitle();
    }
    screen.render();
  }, 1000);

  updateHeader();
  updateFooter(0, "--:--");
  screen.render();
  await refresh();
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
