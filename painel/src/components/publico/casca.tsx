"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import type { DadosPublicos } from "@/lib/publico";
import { Dragao } from "./dragao";
import { Perfil } from "./jogadores";
import { Vagalumes } from "./efeitos";

/** Páginas do site público. `chave` marca a página ativa no menu. */
export const PAGINAS = [
  { chave: "inicio", href: "/", nome: "Início", icone: "🏰", menu: false },
  { chave: "expedicoes", href: "/expedicoes", nome: "Expedições", icone: "🧭", menu: true, destaque: true },
  { chave: "rankings", href: "/rankings", nome: "Rankings", icone: "🏆", menu: true },
  { chave: "aventureiros", href: "/aventureiros", nome: "Aventureiros", icone: "🛡️", menu: true },
  { chave: "mundo", href: "/mundo", nome: "O Mundo", icone: "🌍", menu: true },
  { chave: "biblioteca", href: "/biblioteca", nome: "Biblioteca", icone: "📚", menu: true },
  { chave: "novidades", href: "/novidades", nome: "Novidades", icone: "📰", menu: false },
  { chave: "mods", href: "/mods", nome: "Mods", icone: "🧩", menu: false },
  { chave: "entrar", href: "/entrar", nome: "Como entrar", icone: "🚪", menu: false },
] as const;

/** Endereços antigos (âncoras da página única) → páginas novas. */
const ANTIGOS: Record<string, string> = {
  "#aventureiros": "/aventureiros", "#cronicas": "/aventureiros#cronicas", "#mural": "/aventureiros#mural", "#armas": "/aventureiros#armas",
  "#rankings": "/rankings", "#clas": "/rankings#clas", "#mapa": "/mundo#mapa", "#bestiario": "/mundo#bestiario", "#mundo": "/mundo",
  "#biblioteca": "/biblioteca", "#mods": "/mods", "#novidades": "/novidades", "#entrar": "/entrar",
};

type Site = { d: DadosPublicos | null; abrir: (n: string) => void };
const Contexto = createContext<Site>({ d: null, abrir: () => {} });
export const useSite = () => useContext(Contexto);

/**
 * Casco de todas as páginas públicas: menu fixo, dragão no céu, ficha de jogador e rodapé.
 * Guarda os dados públicos e atualiza a cada 15 s (as páginas leem com useSite()).
 */
export function Casca({ pagina, inicial, children }: { pagina: string; inicial?: DadosPublicos | null; children: ReactNode }) {
  const [d, setD] = useState<DadosPublicos | null>(inicial ?? null);
  const [perfil, setPerfil] = useState<string | null>(null);
  const [menu, setMenu] = useState(false);
  const [mais, setMais] = useState(false);
  const abrir = useCallback((n: string) => setPerfil(n), []);

  useEffect(() => {
    let vivo = true;
    const buscar = () => fetch("/api/publico/dados").then((r) => (r.ok ? r.json() : null)).then((j) => { if (vivo && j && !j.erro) setD(j); }).catch(() => {});
    if (inicial === undefined) buscar();
    const t = setInterval(buscar, 15_000);
    return () => { vivo = false; clearInterval(t); };
  }, [inicial]);

  useEffect(() => {
    if (pagina === "inicio" && ANTIGOS[window.location.hash]) window.location.replace(ANTIGOS[window.location.hash]);
  }, [pagina]);

  // blocos do miolo aparecem suavemente ao rolar a página
  useEffect(() => {
    document.documentElement.classList.add("frx-js");
    if (!("IntersectionObserver" in window)) return;
    const obs = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("frx-visivel"); obs.unobserve(e.target); } }), { rootMargin: "0px 0px -8% 0px" });
    document.querySelectorAll("main > *").forEach((el) => { el.classList.add("frx-revela"); obs.observe(el); });
    return () => obs.disconnect();
  }, []);

  const online = !!d?.online;
  const principais = PAGINAS.filter((p) => p.menu);
  const outras = PAGINAS.filter((p) => !p.menu && p.chave !== "inicio");

  return (
    <Contexto.Provider value={{ d, abrir }}>
      <div className="relative isolate flex min-h-screen flex-col">
        <Dragao />
        <nav className="sticky top-0 z-30 border-b border-ouro-escuro/30 bg-pedra-950/90 shadow-lg shadow-black/40 backdrop-blur-md">
          <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2.5">
            <a href="/" onClick={() => setMenu(false)} className="group flex shrink-0 items-center gap-2.5">
              <span className="grid h-9 w-9 place-items-center rounded-lg border border-ouro-escuro bg-gradient-to-br from-pedra-700 to-pedra-950 text-lg transition group-hover:border-ouro" aria-hidden="true">⚔️</span>
              <span className="leading-none">
                <span className="block font-titulo text-xl text-ouro">FRX-WORLD</span>
                <span className="mt-0.5 block text-[9px] uppercase tracking-[0.28em] text-pedra-500">MMORPG · 1.20.1</span>
              </span>
            </a>

            <div className="ml-3 hidden flex-1 items-center gap-0.5 lg:flex">
              {principais.map((p) => (
                <a key={p.chave} href={p.href} aria-current={pagina === p.chave ? "page" : undefined}
                  className={`relative flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm transition hover:bg-pedra-800 hover:text-ouro ${pagina === p.chave ? "bg-pedra-800 text-ouro" : "text-pedra-500"}`}>
                  {p.nome}
                  {"destaque" in p && p.destaque && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" title="Ao vivo" />}
                </a>
              ))}
              <div className="relative" onMouseLeave={() => setMais(false)}>
                <button type="button" onClick={() => setMais((v) => !v)} aria-expanded={mais}
                  className={`flex items-center gap-1 rounded-md px-2.5 py-1.5 text-sm transition hover:bg-pedra-800 hover:text-ouro ${mais || outras.some((p) => p.chave === pagina) ? "bg-pedra-800 text-ouro" : "text-pedra-500"}`}>
                  Mais <span className={`text-[10px] transition-transform ${mais ? "rotate-180" : ""}`} aria-hidden="true">▼</span>
                </button>
                {mais && (
                  <div className="absolute left-0 top-full w-52 pt-2">
                    <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-1.5 shadow-2xl shadow-black/60">
                      {outras.map((p) => (
                        <a key={p.chave} href={p.href} className={`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition hover:bg-pedra-800 hover:text-ouro ${pagina === p.chave ? "text-ouro" : "text-pedra-500"}`}>
                          <span className="w-5 text-center" aria-hidden="true">{p.icone}</span>{p.nome}
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="ml-auto flex shrink-0 items-center gap-2">
              <span className={`hidden items-center gap-2 rounded-full border px-3 py-1.5 text-xs sm:flex ${online ? "border-green-700/70 bg-green-950/40 text-green-400" : "border-pedra-700 text-pedra-500"}`}>
                <span className={`h-2 w-2 rounded-full ${online ? "animate-pulse bg-green-400" : "bg-sangue"}`} />
                {d ? (online ? `${d.onlineAgora.length} online` : "Servidor desligado") : "…"}
              </span>
              <a href="/conta" aria-current={pagina === "conta" ? "page" : undefined} className="flex items-center gap-1.5 rounded-full bg-gradient-to-b from-ouro to-ouro-escuro px-3.5 py-1.5 text-xs font-semibold text-pedra-950 shadow shadow-black/40 transition hover:brightness-110">
                <span aria-hidden="true">👤</span> Minha conta
              </a>
              <button type="button" onClick={() => setMenu((v) => !v)} aria-expanded={menu} aria-label="Abrir o menu"
                className="grid h-9 w-9 place-items-center rounded-lg border border-pedra-700 text-lg text-pedra-500 hover:border-ouro hover:text-ouro lg:hidden">{menu ? "✕" : "☰"}</button>
            </div>
          </div>

          {menu && (
            <div className="border-t border-pedra-800 bg-pedra-950 lg:hidden">
              <div className="mx-auto grid max-w-6xl grid-cols-2 gap-1.5 px-4 py-3 sm:grid-cols-3">
                {PAGINAS.map((p) => (
                  <a key={p.chave} href={p.href} onClick={() => setMenu(false)}
                    className={`flex items-center gap-2 rounded-lg border px-3 py-2.5 text-sm ${pagina === p.chave ? "border-ouro text-ouro" : "border-pedra-800 text-pedra-500 hover:border-ouro hover:text-ouro"}`}>
                    <span aria-hidden="true">{p.icone}</span>{p.nome}
                    {"destaque" in p && p.destaque && <span className="ml-auto h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />}
                  </a>
                ))}
              </div>
            </div>
          )}
        </nav>

        <div className="flex-1">{children}</div>

        {perfil && <Perfil nick={perfil} fechar={() => setPerfil(null)} />}

        <footer className="mt-16 border-t border-pedra-700 bg-pedra-950/80">
          <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 sm:grid-cols-2 lg:grid-cols-4">
            <div className="sm:col-span-2 lg:col-span-1">
              <p className="font-titulo text-2xl text-ouro">FRX-WORLD</p>
              <p className="mt-2 text-sm text-pedra-500">Servidor brasileiro de Minecraft MMORPG e RPG. Modpack Forge 1.20.1 com classes, raças, Guilda, reinos, chefes e expedições que se renovam.</p>
            </div>
            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.25em] text-ouro-escuro">Jogar</p>
              <ul className="space-y-1.5 text-sm">
                {(["entrar", "expedicoes", "mundo", "biblioteca"] as const).map((k) => { const p = PAGINAS.find((x) => x.chave === k)!; return <li key={k}><a href={p.href} className="text-pedra-500 hover:text-ouro">{p.icone} {p.nome}</a></li>; })}
              </ul>
            </div>
            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.25em] text-ouro-escuro">Comunidade</p>
              <ul className="space-y-1.5 text-sm">
                {(["rankings", "aventureiros", "novidades", "mods"] as const).map((k) => { const p = PAGINAS.find((x) => x.chave === k)!; return <li key={k}><a href={p.href} className="text-pedra-500 hover:text-ouro">{p.icone} {p.nome}</a></li>; })}
              </ul>
            </div>
            <div>
              <p className="mb-2 text-xs uppercase tracking-[0.25em] text-ouro-escuro">Agora</p>
              <p className={`flex items-center gap-2 text-sm ${online ? "text-green-400" : "text-pedra-500"}`}>
                <span className={`h-2 w-2 rounded-full ${online ? "animate-pulse bg-green-400" : "bg-sangue"}`} />
                {online ? `Servidor ligado · ${d?.onlineAgora.length ?? 0} jogando` : "Servidor desligado"}
              </p>
              {d?.status?.versaoMod && <p className="mt-1 text-sm text-pedra-500">Versão {d.status.versaoMod}</p>}
              <a href="/conta" className="mt-3 inline-block text-sm text-ouro-escuro hover:text-ouro">👤 Minha conta</a>
            </div>
          </div>
          <p className="border-t border-pedra-800 px-4 py-4 text-center text-xs text-pedra-500">
            FRX-WORLD · servidor não oficial, sem vínculo com a Mojang ou a Microsoft · <a href="/admin" className="hover:text-ouro">Administração</a>
          </p>
        </footer>
      </div>
    </Contexto.Provider>
  );
}

/** Topo das páginas internas: ícone, sobretítulo, título e uma linha explicando a página. */
export function CabecaPagina({ icone, sobre, titulo, texto, children }: { icone: string; sobre: string; titulo: string; texto: string; children?: ReactNode }) {
  return (
    <header className="frx-heroi relative overflow-hidden border-b border-pedra-700">
      <Vagalumes />
      <div className="relative mx-auto max-w-6xl px-4 py-12 text-center md:py-16">
        <span className="relative mx-auto grid h-16 w-16 place-items-center" aria-hidden="true">
          <span className="frx-runa" /><span className="frx-runa frx-runa2" />
          <span className="frx-flutua grid h-16 w-16 place-items-center rounded-2xl border border-ouro-escuro bg-pedra-950/70 text-3xl shadow-lg shadow-black/50">{icone}</span>
        </span>
        <p className="mt-6 text-xs uppercase tracking-[0.3em] text-ouro-escuro">{sobre}</p>
        <h1 className="frx-brilho mt-2 font-titulo text-4xl drop-shadow-[0_2px_12px_rgba(0,0,0,0.6)] md:text-6xl">{titulo}</h1>
        <p className="mx-auto mt-3 max-w-2xl text-pergaminho/80 md:text-lg">{texto}</p>
        {children}
      </div>
    </header>
  );
}

/** Miolo padrão das páginas. */
export function Miolo({ children }: { children: ReactNode }) {
  return <main className="mx-auto max-w-6xl space-y-12 px-4 py-10">{children}</main>;
}
