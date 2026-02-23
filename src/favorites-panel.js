const blessed = require("blessed");
const state = require("./state");
const { saveConfig } = require("./config");

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
    if (state.favorites.length === 0) {
      list.setItems([compact ? "Aucun favori — F pour ajouter" : " {gray-fg}Aucun favori — F pour ajouter{/gray-fg}"]);
    } else {
      list.setItems(state.favorites.map((f) => ` ${f.from.name} → ${f.to.name}`));
      selectedIndex = Math.min(selectedIndex, state.favorites.length - 1);
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

    if (state.favorites.length === 0) return;

    if (key.name === "down" || key.name === "j") {
      selectedIndex = Math.min(selectedIndex + 1, state.favorites.length - 1);
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
      const fav = state.favorites[selectedIndex];
      cleanup();
      onAction({ type: "load", favorite: fav });
      return;
    }

    if (key.name === "d" || key.name === "x") {
      state.favorites.splice(selectedIndex, 1);
      saveConfig();
      if (state.favorites.length === 0) {
        selectedIndex = 0;
      } else {
        selectedIndex = Math.min(selectedIndex, state.favorites.length - 1);
      }
      refreshList();
      return;
    }
  });

  refreshList();
}

module.exports = { showFavoritesPanel };
