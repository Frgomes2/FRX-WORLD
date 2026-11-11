"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const itens = [
  { href: "/", rotulo: "Visão geral" },
  { href: "/convites", rotulo: "Convites" },
  { href: "/jogadores", rotulo: "Jogadores" },
  { href: "/servidor", rotulo: "Servidor" },
  { href: "/progressao", rotulo: "Progressão" },
  { href: "/missoes", rotulo: "Missões" },
  { href: "/economia", rotulo: "Economia" },
  { href: "/reinos", rotulo: "Reinos" },
  { href: "/npcs", rotulo: "NPCs e skins" },
  { href: "/dungeons", rotulo: "Dungeons" },
  { href: "/masmorras", rotulo: "Masmorras" },
  { href: "/externos", rotulo: "Chefes de mods" },
  { href: "/eventos", rotulo: "Eventos de estrada" },
  { href: "/habilidades", rotulo: "Habilidades" },
  { href: "/forja", rotulo: "Forja" },
  { href: "/admins", rotulo: "Administradores" },
];

// Módulos do documento de design que entram nas próximas fases
const futuros = ["Conteúdo", "Mobs e bosses", "Códex de Armas",
  "Estados", "Lei e crimes", "Rituais", "Eventos globais", "Versões do pack"];

export function Menu({ aoNavegar }: { aoNavegar?: () => void }) {
  const atual = usePathname();
  return (
    <nav className="flex flex-col gap-1 text-sm">
      {itens.map((i) => (
        <Link key={i.href} href={i.href} onClick={aoNavegar}
          className={`rounded-md px-3 py-2 ${atual === i.href || (i.href !== "/" && atual.startsWith(i.href + "/")) ? "bg-pedra-700 text-ouro" : "hover:bg-pedra-800"}`}>
          {i.rotulo}
        </Link>
      ))}
      <p className="mt-6 px-3 text-xs uppercase tracking-wider text-pedra-500">Em breve</p>
      {futuros.map((f) => (
        <span key={f} className="cursor-not-allowed px-3 py-1.5 text-pedra-500/70">{f}</span>
      ))}
    </nav>
  );
}

/** Celular: botão ☰ no topo abre o menu como gaveta por cima da página. */
export function MenuCelular() {
  const [aberto, setAberto] = useState(false);
  const atual = usePathname();
  const titulo = itens.find((i) => atual === i.href || (i.href !== "/" && atual.startsWith(i.href + "/")))?.rotulo ?? "";
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
