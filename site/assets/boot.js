/*
 * Runs before the page shows, so nobody sees the English text or the default glass flash first.
 * Language: ?lang=pt in the address, the visitor's last choice, the browser's languages, English.
 * Glass and wallpaper: the visitor's last choice, or Smoke over Aurora (Kiri's own defaults).
 */
(function () {
  var root = document.documentElement;
  function stored(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }

  var lang = null;
  try { lang = new URLSearchParams(location.search).get("lang"); } catch (e) {}
  if (!lang) lang = stored("kiri-lang");
  if (!lang) {
    var list = navigator.languages || [navigator.language || ""];
    for (var i = 0; i < list.length && !lang; i++) {
      var code = String(list[i] || "").slice(0, 2).toLowerCase();
      if (code === "en" || code === "pt" || code === "es") lang = code;
    }
  }
  if (lang !== "pt" && lang !== "es") lang = "en";
  root.setAttribute("data-lang", lang);
  root.lang = lang === "pt" ? "pt-BR" : lang;
  if (lang !== "en") {
    root.classList.add("i18n-wait");
    setTimeout(function () { root.classList.remove("i18n-wait"); }, 1500);
  }

  var glass = stored("kiri-glass");
  if (glass === "fume" || glass === "cristal" || glass === "nevoa") root.setAttribute("data-glass", glass);
  var wall = stored("kiri-wall");
  if (wall === "aurora" || wall === "oceano" || wall === "grafite" || wall === "por_do_sol") root.setAttribute("data-wall", wall);
  if (stored("kiri-motion") === "off") root.classList.add("still");
})();
