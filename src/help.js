const blessed = require("blessed");
const state = require("./state");

function createHelpOverlay(screen) {
  let helpOpen = false;
  let helpBox = null;

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
      `  {bold}W{/bold} Attente     ${on(state.columns.wait)}   {bold}A{/bold} Aligner   ${on(state.display.aligned)}`,
      `  {bold}D{/bold} Départ      ${on(state.columns.departure)}   {bold}B{/bold} Bandeau   ${on(state.display.banner)}`,
      `  {bold}R{/bold} Arrivée     ${on(state.columns.arrival)}   {bold}H{/bold} En-tête   ${on(state.display.tableHeader)}`,
      `  {bold}U{/bold} Durée       ${on(state.columns.duration)}   {bold}T{/bold} Transport ${routeLabel[state.routeMode]}`,
      "",
      `  {bold}+{/bold}/{bold}-{/bold} Marche départ       ${state.walkDeparture > 0 ? `{magenta-fg}${state.walkDeparture}{/magenta-fg}` : "{gray-fg}0{/gray-fg}"}`,
      `  {bold}]{/bold}/{bold}[{/bold} Marche arrivée      ${state.walkArrival > 0 ? `{cyan-fg}${state.walkArrival}{/cyan-fg}` : "{gray-fg}0{/gray-fg}"}`,
      "",
      "  {bold}Actions{/bold}",
      "  {bold}r{/bold} Rafraîchir     {bold}d{/bold} Départ",
      "  {bold}a{/bold} Arrivée        {bold}q{/bold} Quitter",
      "  {bold}f{/bold} Favoris        {bold}F{/bold} Ajouter favori",
      "  {bold}?{/bold} Fermer l'aide",
      "",
    ].join("\n"));
  }

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

  return {
    toggleHelp,
    refreshHelp,
    isOpen: () => helpOpen,
  };
}

module.exports = { createHelpOverlay };
