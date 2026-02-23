const state = {
  fromStation: null,
  toStation: null,
  departures: [],
  favorites: [],
  display: { banner: true, tableHeader: true, aligned: true },
  columns: { wait: true, departure: true, arrival: true, duration: true },
  routeMode: "full",
  walkDeparture: 0,
  walkArrival: 0,
};

function init(config) {
  state.fromStation = config.from;
  state.toStation = config.to;
  state.display = { ...state.display, ...(config.display || {}) };
  state.columns = { ...state.columns, ...(config.columns || {}) };
  state.routeMode = config.routeMode || "full";
  state.walkDeparture = config.walkDeparture || config.walkMinutes || 0;
  state.walkArrival = config.walkArrival || 0;
  state.favorites = config.favorites || [];
}

module.exports = state;
module.exports.init = init;
