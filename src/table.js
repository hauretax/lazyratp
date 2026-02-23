const blessed = require("blessed");
const state = require("./state");
const { fmtTime, fmtWait, fmtSteps } = require("./format");

function buildRowParts(d, firstWalkPad) {
  const parts = [];

  if (state.columns.wait) parts.push(fmtWait(d));
  if (state.columns.departure) {
    const depDate = new Date(new Date(d.departure).getTime() - state.walkDeparture * 60000);
    const walkTag = state.walkDeparture > 0 ? `{gray-fg}${state.walkDeparture}{/gray-fg} ` : "";
    parts.push(`${walkTag}${fmtTime(depDate.toISOString())}`);
  }
  if (state.columns.arrival) {
    if (d.arrivalAtDest) {
      const arrDate = new Date(new Date(d.arrivalAtDest).getTime() + state.walkArrival * 60000);
      const walkTag = state.walkArrival > 0 ? `{gray-fg}${state.walkArrival}{/gray-fg} ` : "";
      parts.push(`→ ${walkTag}${fmtTime(arrDate.toISOString())}`);
    } else {
      parts.push("→ --:--");
    }
  }
  if (state.columns.duration) {
    let totalSec = state.walkDeparture * 60 + state.walkArrival * 60 + (d.walkAfterLast || 0);
    for (const s of d.steps) {
      totalSec += (s.duration || 0) + (s.walkBefore || 0);
    }
    parts.push(`{magenta-fg}${Math.round(totalSec / 60)}{/magenta-fg}`);
  }
  if (state.routeMode !== "off") parts.push(fmtSteps(d, firstWalkPad));

  return parts;
}

function buildHeaderParts() {
  const parts = [];
  if (state.columns.wait) parts.push("Attente");
  if (state.columns.departure) parts.push("Départ");
  if (state.columns.arrival) parts.push("Arrivée");
  if (state.columns.duration) parts.push("Total");
  if (state.routeMode !== "off") parts.push("Trajet");
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

  let firstWalkPad = 0;
  if (state.routeMode !== "off") {
    for (const d of visible) {
      if (d.steps.length > 0) {
        const w = Math.round(d.steps[0].walkBefore / 60);
        if (w > 0) firstWalkPad = Math.max(firstWalkPad, w.toString().length);
      }
    }
  }

  const allParts = visible.map((d) => buildRowParts(d, firstWalkPad));

  const widths = [];
  if (state.display.aligned) {
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
    if (state.display.aligned) return prefix + padRow(parts, widths);
    return prefix + parts.join("  ");
  });

  if (compact) {
    table.setContent(rows.join("\n"));
  } else if (state.display.tableHeader) {
    const headerParts = buildHeaderParts();
    const headerStr = state.display.aligned
      ? " {bold}{underline}" + padRow(headerParts, widths) + "{/underline}{/bold}"
      : " {bold}{underline}" + headerParts.join("  ") + "{/underline}{/bold}";
    table.setContent(`\n${headerStr}\n\n${rows.join("\n")}`);
  } else {
    table.setContent(rows.join("\n"));
  }
}

module.exports = { renderTable };
