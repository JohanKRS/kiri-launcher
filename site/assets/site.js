/*
 * Kiri website: language, glass and wallpaper choices, the animated wallpaper, and the small
 * moving parts (the logo, the demo clock, the screens gallery, the TV slideshow).
 */
(function () {
  "use strict";
  var root = document.documentElement;
  var reduced = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  function save(key, value) { try { localStorage.setItem(key, value); } catch (e) {} }
  function $all(sel, el) { return Array.prototype.slice.call((el || document).querySelectorAll(sel)); }

  /* ---------- Language ---------- */

  var english = {};
  function remember(el, attr, key) {
    if (!(key in english)) english[key] = attr ? el.getAttribute(attr) : el.innerHTML;
  }
  function applyLang(lang) {
    var table = (window.KIRI_I18N || {})[lang] || {};
    function text(key) { return lang === "en" ? english[key] : (table[key] != null ? table[key] : english[key]); }
    $all("[data-i18n]").forEach(function (el) {
      var key = el.getAttribute("data-i18n");
      remember(el, null, key);
      var value = text(key);
      if (value != null) {
        if (el.tagName === "TITLE") document.title = value;
        else el.textContent = value.replace(/<[^>]+>/g, "");
      }
    });
    $all("[data-i18n-html]").forEach(function (el) {
      var key = el.getAttribute("data-i18n-html");
      remember(el, null, key);
      var value = text(key);
      if (value != null) el.innerHTML = value;
    });
    [["data-i18n-alt", "alt"], ["data-i18n-aria", "aria-label"], ["data-i18n-content", "content"]].forEach(function (pair) {
      $all("[" + pair[0] + "]").forEach(function (el) {
        var key = pair[0] + ":" + el.getAttribute(pair[0]);
        if (!(key in english)) english[key] = el.getAttribute(pair[1]);
        var value = lang === "en" ? english[key] : table[el.getAttribute(pair[0])];
        if (value != null) el.setAttribute(pair[1], value);
      });
    });
    // Screenshots: Portuguese ones for Portuguese, English ones for English and Spanish.
    var shots = lang === "pt" ? "pt" : "en";
    $all("img[data-shot]").forEach(function (img) {
      var name = img.getAttribute("data-shot").split("-");
      var src = "assets/img/" + name[0] + "-" + shots + "-" + name[1] + ".webp";
      if (img.getAttribute("src") !== src) img.setAttribute("src", src);
    });
    root.setAttribute("data-lang", lang);
    root.lang = lang === "pt" ? "pt-BR" : lang;
    $all("[data-set-lang]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-set-lang") === lang)); });
    root.classList.remove("i18n-wait");
    updateClock();
  }

  /* ---------- Glass and wallpaper ---------- */

  function setGlass(glass, keep) {
    root.setAttribute("data-glass", glass);
    $all("[data-set-glass]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-set-glass") === glass)); });
    if (keep) save("kiri-glass", glass);
  }
  function setWall(wall, keep) {
    root.setAttribute("data-wall", wall);
    $all("[data-set-wall]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-set-wall") === wall)); });
    if (keep) save("kiri-wall", wall);
    paper.go(wall);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", WALLS[wall].theme);
  }

  /* ---------- The wallpaper ---------- */

  // Kiri's own TV wallpapers (ui/tv/TvArt.kt): a linear gradient and two soft lights each.
  // Here the lights drift slowly and threads of mist float across, like the logo's.
  var WALLS = {
    aurora: { angle: 135, stops: [[0, "#2B2A48"], [0.48, "#22507F"], [1, "#22495C"]], theme: "#24304f",
      glows: [[1, 0, 0.7, 0.8, "#3F9DB2", 0.62], [0, 1, 0.8, 0.9, "#7A52C4", 0.6]] },
    oceano: { angle: 160, stops: [[0, "#0D2A45"], [0.55, "#0F4F6F"], [1, "#0A2233"]], theme: "#0d2a45",
      glows: [[0.1, 0.9, 0.6, 0.7, "#12365E", 0.7], [0.8, 0.2, 0.7, 0.7, "#2FA3C2", 0.6]] },
    grafite: { angle: 150, stops: [[0, "#1C1F24"], [0.5, "#2A2D33"], [1, "#15171A"]], theme: "#1c1f24",
      glows: [[0.75, 0.25, 0.6, 0.6, "#4A4540", 0.65], [0.15, 0.85, 0.55, 0.55, "#3A3D44", 0.4]] },
    por_do_sol: { angle: 140, stops: [[0, "#3A1F4F"], [0.55, "#A44A5A"], [1, "#E0864A"]], theme: "#3a1f4f",
      glows: [[0, 1, 0.7, 0.8, "#6C2D7A", 0.65], [0.85, 0.15, 0.7, 0.8, "#F2A35A", 0.55]] }
  };

  function hex(c) { return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)]; }
  function mix(a, b, t) { return a + (b - a) * t; }
  function mixRgb(a, b, t) { return [mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)]; }
  function rgba(c, a) { return "rgba(" + (c[0] | 0) + "," + (c[1] | 0) + "," + (c[2] | 0) + "," + a + ")"; }
  function parse(w) {
    return {
      angle: w.angle,
      stops: w.stops.map(function (s) { return [s[0], hex(s[1])]; }),
      glows: w.glows.map(function (g) { return [g[0], g[1], g[2], g[3], hex(g[4]), g[5]]; })
    };
  }
  function blend(a, b, t) {
    return {
      angle: mix(a.angle, b.angle, t),
      stops: a.stops.map(function (s, i) { return [mix(s[0], b.stops[i][0], t), mixRgb(s[1], b.stops[i][1], t)]; }),
      glows: a.glows.map(function (g, i) {
        var h = b.glows[i];
        return [mix(g[0], h[0], t), mix(g[1], h[1], t), mix(g[2], h[2], t), mix(g[3], h[3], t), mixRgb(g[4], h[4], t), mix(g[5], h[5], t)];
      })
    };
  }

  var paper = (function () {
    var canvas = document.querySelector("canvas.wallpaper");
    var ctx = canvas && canvas.getContext("2d");
    var from = parse(WALLS[root.getAttribute("data-wall")] || WALLS.aurora);
    var to = from, changedAt = 0, w = 0, h = 0, scale = 0.5, threads = [], last = 0, running = false;
    var moving = !reduced && !root.classList.contains("still");

    function makeThreads() {
      threads = [];
      var rows = Math.round(h / 26);
      for (var i = 0; i < rows; i++) {
        if (Math.random() < 0.35) continue;
        var y = Math.random();
        var depth = 0.35 + Math.random() * 0.65;
        var x = Math.random() * 1.4 - 0.2;
        var parts = 1 + Math.floor(Math.random() * 3);
        for (var p = 0; p < parts; p++) {
          var len = (20 + Math.random() * 150) * depth;
          threads.push({ x: x, y: y, len: len, depth: depth, speed: (3 + Math.random() * 9) * depth * (Math.random() < 0.5 ? 1 : -1),
                         alpha: 0.035 + Math.random() * 0.08 * depth, phase: Math.random() * 6.28 });
          x += (len + 14 + Math.random() * 60) / Math.max(w, 1);
        }
      }
    }
    function resize() {
      if (!canvas) return;
      w = Math.max(1, Math.round(innerWidth * scale));
      h = Math.max(1, Math.round(innerHeight * scale));
      canvas.width = w;
      canvas.height = h;
      makeThreads();
      draw(performance.now());
    }
    function draw(now) {
      if (!ctx) return;
      var t = Math.min(1, (now - changedAt) / 1400);
      t = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
      var look = t >= 1 ? to : blend(from, to, t);
      var s = moving ? now / 1000 : 0;

      var a = look.angle * Math.PI / 180;
      var dx = Math.sin(a), dy = -Math.cos(a);
      var half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
      var grad = ctx.createLinearGradient(w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half);
      look.stops.forEach(function (st) { grad.addColorStop(st[0], rgba(st[1], 1)); });
      ctx.globalCompositeOperation = "source-over";
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);

      look.glows.forEach(function (g, i) {
        var cx = (g[0] + Math.sin(s * 0.045 + i * 2.1) * 0.13) * w;
        var cy = (g[1] + Math.cos(s * 0.037 + i * 1.3) * 0.11) * h;
        var rx = g[2] * w * (1 + Math.sin(s * 0.06 + i) * 0.07);
        var ry = g[3] * h * (1 + Math.cos(s * 0.05 + i) * 0.07);
        ctx.save();
        ctx.translate(cx, cy);
        ctx.scale(1, ry / rx);
        var r = ctx.createRadialGradient(0, 0, 0, 0, 0, rx);
        r.addColorStop(0, rgba(g[4], g[5]));
        r.addColorStop(0.45, rgba(g[4], g[5] * 0.45));
        r.addColorStop(1, rgba(g[4], 0));
        ctx.fillStyle = r;
        ctx.fillRect(-rx, -rx, rx * 2, rx * 2);
        ctx.restore();
      });

      // Threads of mist.
      var scroll = (window.scrollY || 0) * scale;
      ctx.lineCap = "round";
      threads.forEach(function (th) {
        var x = ((th.x * w + th.speed * s * scale * 2) % (w * 1.4) + w * 1.4) % (w * 1.4) - w * 0.2;
        var y = ((th.y * h - scroll * th.depth * 0.18) % h + h) % h;
        var alpha = th.alpha * (0.65 + 0.35 * Math.sin(s * 0.3 + th.phase));
        ctx.strokeStyle = "rgba(246,242,255," + alpha + ")";
        ctx.lineWidth = Math.max(1, 3.4 * th.depth * scale * 2);
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x + th.len * scale * 2, y);
        ctx.stroke();
      });
    }
    function frame(now) {
      if (!running) return;
      if (now - last > 33) { last = now; draw(now); }
      requestAnimationFrame(frame);
    }
    function start() {
      if (running || !ctx) return;
      running = true;
      requestAnimationFrame(frame);
    }
    function stop() { running = false; }

    if (canvas) {
      resize();
      addEventListener("resize", resize);
      document.addEventListener("visibilitychange", function () {
        if (document.hidden) stop(); else if (moving) start();
      });
      addEventListener("scroll", function () { if (!moving) draw(performance.now()); }, { passive: true });
      if (moving) start();
    }

    return {
      go: function (name) {
        var next = parse(WALLS[name] || WALLS.aurora);
        var now = performance.now();
        var t = Math.min(1, (now - changedAt) / 1400);
        from = t >= 1 ? to : blend(from, to, t);
        to = next;
        changedAt = now;
        if (!moving) {
          (function fade(n) { draw(n); if (n - changedAt < 1450) requestAnimationFrame(fade); })(now);
        }
      },
      motion: function (on) {
        moving = on && !reduced;
        root.classList.toggle("still", !on);
        if (moving) start(); else { stop(); draw(performance.now()); }
      }
    };
  })();

  /* ---------- The logo, drawn thread by thread ---------- */

  function drawMarks() {
    var marks = $all(".mark");
    if (!marks.length || !window.fetch) return;
    fetch("assets/brand/logo.svg").then(function (r) { return r.text(); }).then(function (svg) {
      marks.forEach(function (mark, n) {
        mark.innerHTML = svg.replace("<svg ", '<svg aria-hidden="true" ').replace(/id="k"/, 'id="k' + n + '"').replace(/url\(#k\)/g, "url(#k" + n + ")");
        $all("path", mark).forEach(function (p) {
          var m = /M([\d.]+) [\d.]+H([\d.]+)/.exec(p.getAttribute("d"));
          if (m) p.style.setProperty("--d", (parseFloat(m[1]) + parseFloat(m[2])) / 2 < 54 ? -1 : 1);
        });
        if (!mark.classList.contains("small") && !reduced) {
          var count = $all("path", mark).length;
          setTimeout(function () { mark.classList.add("breathing"); }, count * 38 + 1300);
        }
      });
    }).catch(function () {});
  }

  /* ---------- Demo clock ---------- */

  function updateClock() {
    var time = document.getElementById("demo-time");
    var date = document.getElementById("demo-date");
    if (!time) return;
    var lang = root.getAttribute("data-lang");
    var locale = lang === "pt" ? "pt-BR" : lang === "es" ? "es" : "en";
    var now = new Date();
    try {
      time.textContent = now.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
      var d = now.toLocaleDateString(locale, { weekday: "long", day: "numeric", month: "long" });
      date.textContent = d.charAt(0).toUpperCase() + d.slice(1);
    } catch (e) {}
  }

  /* ---------- Gallery, TV, version, reveal ---------- */

  function gallery() {
    var strip = document.querySelector(".gallery");
    if (!strip) return;
    $all("[data-scroll]").forEach(function (b) {
      b.addEventListener("click", function () {
        var shot = strip.querySelector(".shot");
        var step = shot ? shot.getBoundingClientRect().width + 34 : 300;
        strip.scrollBy({ left: step * parseInt(b.getAttribute("data-scroll"), 10), behavior: reduced ? "auto" : "smooth" });
      });
    });
  }

  function tv() {
    var imgs = $all("#tv-screen img");
    var dots = $all("[data-tv]");
    if (!imgs.length) return;
    var at = 0, timer = null;
    function show(i) {
      at = (i + imgs.length) % imgs.length;
      imgs.forEach(function (im, n) { im.classList.toggle("on", n === at); });
      dots.forEach(function (d, n) { d.classList.toggle("on", n === at); d.setAttribute("aria-pressed", String(n === at)); });
    }
    function play() {
      clearInterval(timer);
      if (!reduced) timer = setInterval(function () { show(at + 1); }, 4200);
    }
    dots.forEach(function (d) { d.addEventListener("click", function () { show(parseInt(d.getAttribute("data-tv"), 10)); play(); }); });
    play();
  }

  function version() {
    var tag = document.getElementById("version");
    if (!tag || !window.fetch) return;
    fetch("https://api.github.com/repos/JohanKRS/kiri-launcher/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        if (data && /^v?\d+(\.\d+)*$/.test(data.tag_name || "")) {
          tag.textContent = data.tag_name.charAt(0) === "v" ? data.tag_name : "v" + data.tag_name;
          tag.hidden = false;
        }
      }).catch(function () {});
  }

  function reveal() {
    var items = $all(".section-copy, .section-head, .demo, .shot, .features li, .tv-device, .privacy, .closing-in, .doc-in");
    if (reduced || !("IntersectionObserver" in window)) return;
    items.forEach(function (el) { el.classList.add("reveal"); });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add("in");
          io.unobserve(e.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px" });
    items.forEach(function (el) { io.observe(el); });
  }

  function tweakPanel() {
    var button = document.querySelector(".tweak");
    var panel = document.getElementById("tweak-panel");
    if (!button || !panel) return;
    function toggle(open) {
      panel.hidden = !open;
      button.setAttribute("aria-expanded", String(open));
    }
    button.addEventListener("click", function (e) { e.stopPropagation(); toggle(panel.hidden); });
    document.addEventListener("click", function (e) { if (!panel.hidden && !panel.contains(e.target)) toggle(false); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") toggle(false); });
    var motion = document.getElementById("motion");
    if (motion) {
      motion.checked = !root.classList.contains("still");
      motion.addEventListener("change", function () {
        paper.motion(motion.checked);
        save("kiri-motion", motion.checked ? "on" : "off");
      });
    }
  }

  /* ---------- Start ---------- */

  document.addEventListener("click", function (e) {
    var t = e.target.closest && e.target.closest("[data-set-lang],[data-set-glass],[data-set-wall]");
    if (!t) return;
    if (t.hasAttribute("data-set-lang")) {
      var lang = t.getAttribute("data-set-lang");
      save("kiri-lang", lang);
      applyLang(lang);
      try {
        var url = new URL(location.href);
        if (url.searchParams.has("lang")) { url.searchParams.set("lang", lang); history.replaceState(null, "", url); }
      } catch (err) {}
    } else if (t.hasAttribute("data-set-glass")) {
      setGlass(t.getAttribute("data-set-glass"), true);
    } else {
      setWall(t.getAttribute("data-set-wall"), true);
    }
  });

  applyLang(root.getAttribute("data-lang") || "en");
  setGlass(root.getAttribute("data-glass") || "fume", false);
  $all("[data-set-wall]").forEach(function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-set-wall") === root.getAttribute("data-wall"))); });
  drawMarks();
  updateClock();
  setInterval(updateClock, 15000);
  gallery();
  tv();
  version();
  reveal();
  tweakPanel();
})();
