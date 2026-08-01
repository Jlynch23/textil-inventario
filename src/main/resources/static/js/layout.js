// Scripts globales del layout (#13, auditoría): sidebar, acordeón del menú,
// toggle de modo oscuro y memoria de scroll del menú. Se extrajo del <script
// th:inline> de base.html para reducir el JS inline (paso hacia una CSP estricta).
//
// El único dato del servidor que necesitaba (sidebarInicialColapsado, true para
// GERENTE) llega por el atributo data-sidebar-colapsado del <body>, así este
// archivo es JS 100% estático y cacheable, sin interpolación Thymeleaf.
//
// Se carga SIN defer al final del <body>: para entonces el sidebar ya está en el
// DOM, y correr sincrónico antes del primer paint evita el parpadeo del sidebar
// expandiéndose antes de colapsar.

// Boton para ocultar/mostrar el sidebar (util en monitores chicos).
// La preferencia se guarda en localStorage para que se mantenga
// al navegar entre paginas (cada pagina es una carga nueva del
// layout, no una SPA).
(function () {
    const body = document.body;
    const btn = document.getElementById('btnToggleSidebar');
    const colapsarPorDefecto = body.dataset.sidebarColapsado === 'true';
    // Clave separada para GERENTE: si se usa la misma clave que el resto
    // de los roles, una preferencia guardada previamente con OTRA cuenta
    // en el mismo navegador (ej. SUPERADMIN dejando el sidebar abierto)
    // anularia el colapso por defecto de GERENTE sin que tenga sentido,
    // ya que localStorage es compartido por navegador, no por usuario.
    const claveStorage = colapsarPorDefecto ? 'sidebarColapsadoGerente' : 'sidebarColapsado';
    const guardado = localStorage.getItem(claveStorage);
    if (guardado === '1' || (guardado === null && colapsarPorDefecto)) {
        body.classList.add('sidebar-collapsed');
    }
    if (btn) {
        btn.addEventListener('click', function () {
            body.classList.toggle('sidebar-collapsed');
            localStorage.setItem(claveStorage, body.classList.contains('sidebar-collapsed') ? '1' : '0');
        });
    }
})();

// Acordeon del menu: las secciones con [data-grupo] (Analisis, Catalogo)
// se pliegan/despliegan y recuerdan su estado en localStorage. Catalogo
// arranca plegado por defecto (son 8 pantallas de configuracion que casi
// no se tocan en el dia a dia) para que el menu no quede tan largo.
(function () {
    document.querySelectorAll('.sidebar .nav-group[data-grupo]').forEach(function (grupo) {
        const nombre = grupo.getAttribute('data-grupo');
        const clave = 'navGrupo_' + nombre;
        const guardado = localStorage.getItem(clave);
        const plegar = (guardado === null) ? (nombre === 'catalogo') : (guardado === '1');
        if (plegar) grupo.classList.add('nav-collapsed');
        const btn = grupo.querySelector('.nav-toggle');
        if (btn) {
            btn.addEventListener('click', function () {
                grupo.classList.toggle('nav-collapsed');
                try { localStorage.setItem(clave, grupo.classList.contains('nav-collapsed') ? '1' : '0'); } catch (e) {}
            });
        }
    });
})();

// Modo oscuro: alterna data-bs-theme en <html> y guarda la preferencia.
// El tema ya se aplico al cargar (script en <head>); aca solo el toggle
// manual y el icono (luna = activar oscuro / sol = volver a claro).
(function () {
    const btn = document.getElementById('btnToggleTheme');
    if (!btn) return;
    const icono = btn.querySelector('i');
    function pintarIcono() {
        const dark = document.documentElement.getAttribute('data-bs-theme') === 'dark';
        if (icono) icono.className = (dark ? 'bi bi-sun fs-6' : 'bi bi-moon-stars fs-6');
    }
    pintarIcono();
    btn.addEventListener('click', function () {
        const html = document.documentElement;
        const dark = html.getAttribute('data-bs-theme') === 'dark';
        if (dark) { html.removeAttribute('data-bs-theme'); } else { html.setAttribute('data-bs-theme', 'dark'); }
        try { localStorage.setItem('theme', dark ? 'light' : 'dark'); } catch (e) {}
        pintarIcono();
    });
})();

// El menu lateral recuerda hasta donde lo bajaste. Cada clic es una
// carga nueva de pagina (no es SPA), asi que sin esto el <nav> vuelve
// siempre arriba y las opciones del final quedan fuera de vista.
// sessionStorage (no localStorage): se limpia al cerrar la pestana.
(function () {
    const nav = document.querySelector('.sidebar nav');
    if (!nav) return;
    const CLAVE = 'sidebarScroll';
    try {
        const guardado = sessionStorage.getItem(CLAVE);
        if (guardado) nav.scrollTop = parseInt(guardado, 10) || 0;
    } catch (e) {}
    let pendiente = false;
    nav.addEventListener('scroll', function () {
        if (pendiente) return;
        pendiente = true;
        requestAnimationFrame(function () {
            pendiente = false;
            try { sessionStorage.setItem(CLAVE, nav.scrollTop); } catch (e) {}
        });
    }, { passive: true });
})();
