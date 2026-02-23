const blessed = require("blessed");
const state = require("./state");
const { fmtClock } = require("./format");
const { renderTable } = require("./table");

function createUI() {
  const screen = blessed.screen({
    smartCSR: true,
    title: `${state.fromStation.name} → ${state.toStation.name}`,
    fullUnicode: true,
  });

  const header = blessed.box({
    parent: screen,
    top: 0,
    left: 0,
    width: "100%",
    height: 3,
    tags: true,
    style: { bg: "red", fg: "white", bold: true },
  });

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
    const title = ` {bold}${state.fromStation.name}{/bold} → ${state.toStation.name}`;
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

  function applyLayout() {
    const compact = screen.height < 10 || screen.width < 50;
    const showBanner = !compact && state.display.banner;

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
    if (state.departures.length > 0) renderTable(table, state.departures, screen);
    screen.render();
  });

  applyLayout();

  let pickerOpen = false;

  screen.key(["q", "C-c", "escape"], () => {
    if (!pickerOpen) process.exit(0);
  });

  return {
    screen,
    header,
    table,
    footer,
    updateHeader,
    updateFooter,
    updateCompactClock,
    applyLayout,
    isCompact: () => screen.height < 10 || screen.width < 50,
    setPickerOpen: (v) => { pickerOpen = v; },
    getPickerOpen: () => pickerOpen,
  };
}

module.exports = { createUI };
