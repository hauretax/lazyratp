const fs = require("fs");
const path = require("path");
const state = require("./state");

const CONFIG_PATH = path.join(__dirname, "..", "config.json");

// L'ancien id de La Ferté (63498) a disparu du référentiel IDFM : l'API répond
// unknown_object, et le premier lancement échouait donc sur "API error: 404".
const DEFAULTS = {
  from: { id: "stop_area:IDFM:68918", name: "La Ferté-sous-Jouarre" },
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
    from: state.fromStation,
    to: state.toStation,
    display: state.display,
    columns: state.columns,
    routeMode: state.routeMode,
    walkDeparture: state.walkDeparture,
    walkArrival: state.walkArrival,
    favorites: state.favorites,
  }, null, 2));
}

module.exports = { DEFAULTS, loadConfig, saveConfig };
