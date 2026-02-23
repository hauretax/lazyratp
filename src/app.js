const state = require("./state");
const { loadConfig, saveConfig } = require("./config");
const { checkApiKey, fetchJourneys } = require("./api");
const { fmtClock, updateProcessTitle } = require("./format");
const { createUI } = require("./screen");
const { renderTable } = require("./table");
const { showStationPicker } = require("./station-picker");
const { showFavoritesPanel } = require("./favorites-panel");
const { createHelpOverlay } = require("./help");

const REFRESH_INTERVAL = 600000; // 10 min

checkApiKey();
state.init(loadConfig());

async function main() {
  const {
    screen, table, footer,
    updateHeader, updateFooter, updateCompactClock,
    applyLayout, isCompact,
    setPickerOpen, getPickerOpen,
  } = createUI();

  const help = createHelpOverlay(screen);

  let refreshTimer = null;

  function scheduleRefresh() {
    if (refreshTimer) clearTimeout(refreshTimer);

    if (state.departures.length === 0) {
      refreshTimer = setTimeout(refresh, REFRESH_INTERVAL);
      return;
    }

    const lastDeparture = state.departures[state.departures.length - 1];
    const lastTime = new Date(lastDeparture.departure).getTime();
    const delay = Math.max(lastTime - Date.now() + 5000, 10000);
    refreshTimer = setTimeout(refresh, delay);
  }

  async function refresh() {
    try {
      state.departures = await fetchJourneys();
      state.departures.sort((a, b) => new Date(a.departure) - new Date(b.departure));
      state.departures = state.departures.filter((d) => new Date(d.departure) > new Date());
      renderTable(table, state.departures, screen);
      updateFooter(state.departures.length, fmtClock());
      updateProcessTitle();
      scheduleRefresh();
    } catch (err) {
      table.setContent(`\n {red-fg}Erreur: ${err.message}{/red-fg}`);
      refreshTimer = setTimeout(refresh, REFRESH_INTERVAL);
    }
    screen.render();
  }

  function rerender() {
    applyLayout();
    renderTable(table, state.departures, screen);
    if (help.isOpen()) help.refreshHelp();
    saveConfig();
    screen.render();
  }

  // Column toggles
  function toggleColumn(key) {
    if (getPickerOpen()) return;
    state.columns[key] = !state.columns[key];
    rerender();
  }

  screen.key(["S-w"], () => toggleColumn("wait"));
  screen.key(["S-d"], () => toggleColumn("departure"));
  screen.key(["S-r"], () => toggleColumn("arrival"));
  screen.key(["S-u"], () => toggleColumn("duration"));

  // Display toggles
  screen.key(["S-b"], () => {
    if (getPickerOpen()) return;
    state.display.banner = !state.display.banner;
    rerender();
  });
  screen.key(["S-a"], () => {
    if (getPickerOpen()) return;
    state.display.aligned = !state.display.aligned;
    rerender();
  });
  screen.key(["S-h"], () => {
    if (getPickerOpen()) return;
    state.display.tableHeader = !state.display.tableHeader;
    rerender();
  });
  screen.key(["S-t"], () => {
    if (getPickerOpen()) return;
    const cycle = { off: "code", code: "full", full: "off" };
    state.routeMode = cycle[state.routeMode];
    rerender();
  });

  // Walk times
  screen.key(["+", "="], () => {
    if (getPickerOpen()) return;
    state.walkDeparture++;
    rerender();
  });
  screen.key(["-"], () => {
    if (getPickerOpen()) return;
    if (state.walkDeparture > 0) state.walkDeparture--;
    rerender();
  });
  screen.key(["]"], () => {
    if (getPickerOpen()) return;
    state.walkArrival++;
    rerender();
  });
  screen.key(["["], () => {
    if (getPickerOpen()) return;
    if (state.walkArrival > 0) state.walkArrival--;
    rerender();
  });

  screen.key(["?"], () => { if (!getPickerOpen()) help.toggleHelp(); });

  // Refresh
  screen.key(["r"], () => { if (!getPickerOpen()) refresh(); });

  // Station pickers
  function openPicker(label, onSelect) {
    setPickerOpen(true);
    showStationPicker(screen, label, isCompact(), async (station) => {
      setPickerOpen(false);
      if (station) {
        onSelect(station);
        saveConfig();
        screen.title = `${state.fromStation.name} → ${state.toStation.name}`;
        updateHeader();
        await refresh();
      }
    });
  }

  screen.key(["d"], () => {
    if (getPickerOpen()) return;
    openPicker("Gare de départ", (s) => { state.fromStation = s; });
  });

  screen.key(["a"], () => {
    if (getPickerOpen()) return;
    openPicker("Gare d'arrivée", (s) => { state.toStation = s; });
  });

  // Favorites
  screen.key(["f"], () => {
    if (getPickerOpen()) return;
    setPickerOpen(true);
    showFavoritesPanel(screen, isCompact(), async (action) => {
      setPickerOpen(false);
      if (action && action.type === "load") {
        state.fromStation = action.favorite.from;
        state.toStation = action.favorite.to;
        saveConfig();
        screen.title = `${state.fromStation.name} → ${state.toStation.name}`;
        updateHeader();
        await refresh();
      }
    });
  });

  screen.key(["S-f"], () => {
    if (getPickerOpen()) return;
    const exists = state.favorites.some(
      (f) => f.from.id === state.fromStation.id && f.to.id === state.toStation.id
    );
    if (!exists) {
      state.favorites.push({ from: { ...state.fromStation }, to: { ...state.toStation } });
      saveConfig();
    }
    const origContent = footer.getContent();
    footer.setContent(`\n {green-fg}${exists ? "Déjà en favoris" : "Favori ajouté !"}{/green-fg}  ${state.fromStation.name} → ${state.toStation.name}`);
    screen.render();
    setTimeout(() => {
      updateFooter(state.departures.length, fmtClock());
      screen.render();
    }, 2000);
  });

  // Clock + live wait time update
  setInterval(() => {
    updateHeader();
    updateCompactClock();
    if (state.departures.length > 0) {
      state.departures = state.departures.filter((d) => new Date(d.departure) > new Date());
      renderTable(table, state.departures, screen);
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
