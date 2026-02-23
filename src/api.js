const state = require("./state");
const { pad } = require("./format");

const API_KEY = process.env.TRAIN_API_KEY;
const BASE_URL = "https://prim.iledefrance-mobilites.fr/marketplace/v2/navitia";
const SEARCH_URL = `${BASE_URL}/places`;
const JOURNEYS_URL = `${BASE_URL}/journeys`;

function checkApiKey() {
  if (!API_KEY) {
    console.error("TRAIN_API_KEY manquant. Export la variable : export TRAIN_API_KEY=ta_cle");
    process.exit(1);
  }
}

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
      return { id: sa.id, name: sa.name, modes, city };
    });
}

function parseNavitiaTime(str) {
  if (!str) return null;
  const y = str.slice(0, 4), m = str.slice(4, 6), d = str.slice(6, 8);
  const h = str.slice(9, 11), mi = str.slice(11, 13), s = str.slice(13, 15);
  return `${y}-${m}-${d}T${h}:${mi}:${s}`;
}

async function fetchJourneys() {
  const now = new Date();
  const dt = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}T${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const from = state.fromStation.id;
  const to = state.toStation.id;
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
      code: steps[0]?.code || "",
      dest: steps.length === 1
        ? steps[0].direction
        : `${steps.length} corresp.`,
      platform: "",
    };
  });
}

module.exports = { checkApiKey, searchStations, fetchJourneys };
