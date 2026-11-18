"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

type Item = { href: string; rotulo: string; icone: string };
const grupos: { nome: string; itens: Item[] }[] = [
  { nome: "Geral", itens: [
    { href: "/admin", rotulo: "Visão geral", icone: "🏠" },
    { href: "/admin/servidor", rotulo: "Servidor", icone: "🖥️" },
  ] },
  { nome: "Jogadores", itens: [
    { href: "/admin/jogadores", rotulo: "Jogadores", icone: "🧑" },
    { href: "/admin/convites", rotulo: "Convites", icone: "✉️" },
    { href: "/admin/dinheiro", rotulo: "Dinheiro dos jogadores", icone: "💰" },
    { href: "/admin/admins", rotulo: "Administradores", icone: "🔑" },
  ] },
  { nome: "Progressão e combate", itens: [
    { href: "/admin/progressao", rotulo: "Progressão", icone: "📈" },
    { href: "/admin/habilidades", rotulo: "Habilidades", icone: "✨" },
    { href: "/admin/missoes", rotulo: "Missões", icone: "📜" },
    { href: "/admin/criaturas", rotulo: "Códex de Criaturas", icone: "🐲" },
  ] },
  { nome: "Economia e itens", itens: [
    { href: "/admin/economia", rotulo: "Economia", icone: "🏪" },
    { href: "/admin/forja", rotulo: "Forja", icone: "⚒️" },
    { href: "/admin/codex", rotulo: "Códex de Armas", icone: "🗡️" },
    { href: "/admin/discos", rotulo: "Discos de música", icone: "💿" },
  ] },
  { nome: "Mundo", itens: [
    { href: "/admin/reinos", rotulo: "Reinos", icone: "👑" },
    { href: "/admin/npcs", rotulo: "NPCs e skins", icone: "🧙" },
    { href: "/admin/masmorras", rotulo: "Masmorras", icone: "🏰" },
    { href: "/admin/dungeons", rotulo: "Dungeons", icone: "🕳️" },
    { href: "/admin/externos", rotulo: "Chefes de mods", icone: "☠️" },
    { href: "/admin/eventos", rotulo: "Eventos de estrada", icone: "🛤️" },
  ] },
];
const itens = grupos.flatMap((g) => g.itens);
const ativo = (atual: string, href: string) => atual === href || (href !== "/admin" && atual.startsWith(href + "/"));

// Módulos do documento de design que entram nas próximas fases
const futuros = ["Estados", "Lei e crimes", "Rituais", "Eventos globais"];
const CHAVE = "frx-menu-fechados";

export function Menu({ aoNavegar }: { aoNavegar?: () => void }) {
  const atual = usePathname();
  const [fechados, setFechados] = useState<string[]>([]);
  useEffect(() => {
    try { const v = JSON.parse(window.localStorage.getItem(CHAVE) ?? "[]"); if (Array.isArray(v)) setFechados(v.filter((x) => typeof x === "string")); } catch { /* sem armazenamento: tudo aberto */ }
  }, []);
  const alternar = (nome: string) => setFechados((l) => {
    const novo = l.includes(nome) ? l.filter((x) => x !== nome) : [...l, nome];
    try { window.localStorage.setItem(CHAVE, JSON.stringify(novo)); } catch { /* */ }
    return novo;
  });
  return (
    <nav className="flex flex-col gap-3 text-sm">
      {grupos.map((g) => {
        const temAtivo = g.itens.some((i) => ativo(atual, i.href));
        const aberto = temAtivo || !fechados.includes(g.nome);   // o grupo da tela atual nunca fica escondido
        return (
          <div key={g.nome}>
            <button type="button" onClick={() => alternar(g.nome)} aria-expanded={aberto}
              className="flex w-full items-center justify-between px-3 pb-1 text-left text-[11px] uppercase tracking-wider text-pedra-500 hover:text-ouro">
              <span>{g.nome}</span><span aria-hidden="true">{aberto ? "▾" : "▸"}</span>
            </button>
            {aberto && (
              <div className="flex flex-col gap-0.5">
                {g.itens.map((i) => (
                  <Link key={i.href} href={i.href} onClick={aoNavegar}
                    className={`flex items-center gap-2.5 rounded-md px-3 py-1.5 ${ativo(atual, i.href) ? "bg-pedra-700 text-ouro" : "hover:bg-pedra-800"}`}>
                    <span className="w-5 shrink-0 text-center text-base leading-none" aria-hidden="true">{i.icone}</span>
                    <span className="truncate">{i.rotulo}</span>
                  </Link>
                ))}
              </div>
            )}
          </div>
        );
      })}
      <a href="/" target="_blank" rel="noreferrer" className="rounded-md border border-pedra-700 px-3 py-2 text-pedra-500 hover:border-ouro hover:text-ouro">↗ Ver o site público</a>
      <details className="px-3 text-pedra-500/70">
        <summary className="cursor-pointer text-[11px] uppercase tracking-wider text-pedra-500">Em breve</summary>
        <div className="mt-1 flex flex-col">{futuros.map((f) => <span key={f} className="cursor-not-allowed py-1">{f}</span>)}</div>
      </details>
    </nav>
  );
}

/** Celular: botão ☰ no topo abre o menu como gaveta por cima da página. */
export function MenuCelular() {
  const [aberto, setAberto] = useState(false);
  const atual = usePathname();
  const titulo = itens.find((i) => ativo(atual, i.href))?.rotulo ?? "";
  return (
    <div className="flex min-w-0 items-center gap-3 md:hidden">
      <button type="button" aria-label="Abrir menu" onClick={() => setAberto(true)}
        className="rounded-md border border-pedra-700 px-2.5 py-1 text-lg leading-none hover:border-ouro">☰</button>
      <span className="truncate font-titulo text-lg text-ouro">{titulo || "FRX-WORLD"}</span>
      {aberto && (
        <div className="fixed inset-0 z-50 flex" role="dialog" aria-modal="true">
          <div className="h-full w-72 max-w-[85vw] overflow-y-auto border-r border-pedra-700 bg-pedra-900 p-4">
            <div className="mb-6 flex items-center justify-between px-3">
              <div>
                <p className="font-titulo text-2xl text-ouro">FRX-WORLD</p>
                <p className="text-xs text-pedra-500">Painel admin</p>
              </div>
              <button type="button" aria-label="Fechar menu" onClick={() => setAberto(false)}
                className="rounded-md px-2 py-1 text-xl text-pedra-500 hover:text-ouro">✕</button>
            </div>
            <Menu aoNavegar={() => setAberto(false)} />
          </div>
          <button type="button" aria-label="Fechar menu" className="h-full flex-1 bg-black/60" onClick={() => setAberto(false)} />
        </div>
      )}
    </div>
  );
}
