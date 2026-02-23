const blessed = require("blessed");
const { searchStations } = require("./api");

function showStationPicker(screen, label, compact, onDone) {
  let results = [];
  let searchTimeout = null;
  let query = "";
  let selectedIndex = 0;
  let focusOn = "input";

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

module.exports = { showStationPicker };
