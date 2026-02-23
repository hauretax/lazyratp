const state = require("./state");

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
  const next = state.departures.find((d) => d.status !== "cancelled" && d.arrivalAtDest);
  if (!next) { process.title = "--:--"; return; }
  const arrDate = new Date(new Date(next.arrivalAtDest).getTime() + state.walkArrival * 60000);
  process.title = fmtTime(arrDate.toISOString());
}

function fmtWait(dep) {
  if (dep.status === "cancelled") return "{red-fg}Annulé{/red-fg}";
  const diffMin = Math.round((new Date(dep.departure) - new Date()) / 60000);
  if (diffMin <= 0) return "{yellow-fg}à quai{/yellow-fg}";
  if (state.walkDeparture > 0 && diffMin <= state.walkDeparture) {
    return `{magenta-fg}${diffMin} min{/magenta-fg}`;
  }
  if (diffMin === 1) return "{green-fg}1 min{/green-fg}";
  return `{green-fg}${diffMin} min{/green-fg}`;
}

function fmtSteps(d, firstWalkPad) {
  if (state.routeMode === "off") return "";
  const { steps } = d;
  const cancelled = d.status === "cancelled";
  const segments = [];
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    const code = cancelled ? `{red-fg}${s.code}{/red-fg}` : s.code;
    const mode = state.routeMode === "full" ? `{gray-fg}${s.mode}{/gray-fg} ` : "";
    const dur = s.duration ? ` {blue-fg}${Math.round(s.duration / 60)}{/blue-fg}` : "";
    const walkMin = Math.round(s.walkBefore / 60);
    let walk;
    if (i === 0) {
      const p = firstWalkPad || 0;
      if (walkMin > 0) {
        walk = `{gray-fg}${walkMin.toString().padStart(p)}{/gray-fg} `;
      } else if (p > 0) {
        walk = " ".repeat(p) + " ";
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

module.exports = { pad, fmtClock, fmtTime, updateProcessTitle, fmtWait, fmtSteps };
