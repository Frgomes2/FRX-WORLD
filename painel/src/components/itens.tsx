"use client";

import { useEffect, useMemo, useRef, useState } from "react";

/** Itens do modpack com ícone (tirado dos jars), seletor com busca e campos de moedas. */

export type ItemJogo = { id: string; nome: string; mod: string; pilha: number };

let lista: Promise<ItemJogo[] | null> | null = null;
function carregar() {
  lista ??= fetch("/api/painel/itens", { cache: "no-store" }).then((r) => r.json()).then((j) => j.itens ?? null).catch(() => null);
  return lista;
}

export function useItens() {
  const [c, setC] = useState<ItemJogo[] | null | undefined>(undefined);
  useEffect(() => { let vivo = true; carregar().then((x) => { if (vivo) setC(x); }); return () => { vivo = false; }; }, []);
  return c;
}

export function useMapaItens() {
  const itens = useItens();
  return useMemo(() => new Map((itens ?? []).map((i) => [i.id, i])), [itens]);
}

export function IconeItem({ id, tam = 24, reserva }: { id: string; tam?: number; reserva?: React.ReactNode }) {
  const [falhou, setFalhou] = useState(false);
  const ref = useRef<HTMLImageElement>(null);
  useEffect(() => { const im = ref.current; if (im && im.complete && im.naturalWidth === 0) setFalhou(true); }, [id]);
  const base = id.split("#")[0];
  if (falhou) {
    if (reserva) return <>{reserva}</>;
    const nome = base.split(":")[1] ?? base;
    return <span className="grid shrink-0 place-items-center rounded bg-pedra-700 text-[9px] font-bold uppercase" style={{ width: tam, height: tam }}>{nome.slice(0, 3)}</span>;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img ref={ref} src={`/api/painel/item-icone?id=${encodeURIComponent(base)}`} alt="" width={tam} height={tam} onError={() => setFalhou(true)}
    className="shrink-0 object-cover object-top [image-rendering:pixelated]" style={{ width: tam, height: tam }} />;
}

export function NomeItem({ id, mapa }: { id: string; mapa: Map<string, ItemJogo> }) {
  const base = id.split("#")[0];
  const i = mapa.get(base);
  const extra = id.includes("#") ? ` (${id.split("#")[1].replace(/_/g, " ")})` : "";
  return <>{(i?.nome ?? base.split(":")[1]?.replace(/_/g, " ") ?? base) + extra}</>;
}

const LIMITE = 240;

export function SeletorItem({ titulo, aoEscolher, aoFechar }: { titulo: string; aoEscolher: (id: string) => void; aoFechar: () => void }) {
  const todos = useItens();
  const [busca, setBusca] = useState("");
  const [mod, setMod] = useState("todos");
  const mods = useMemo(() => [...new Set((todos ?? []).map((i) => i.mod))].sort(), [todos]);
  const filtrados = useMemo(() => {
    const q = busca.trim().toLowerCase();
    return (todos ?? []).filter((i) => (mod === "todos" || i.mod === mod) && (!q || i.nome.toLowerCase().includes(q) || i.id.includes(q)));
  }, [todos, busca, mod]);

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4" onClick={aoFechar}>
      <div className="flex max-h-[88vh] w-full max-w-5xl flex-col rounded-2xl border border-ouro/30 bg-pedra-900 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex flex-wrap items-center gap-2 border-b border-pedra-700 p-4">
          <h3 className="mr-2 font-titulo text-xl text-ouro">{titulo}</h3>
          <input autoFocus value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="🔎 Buscar item… (ex.: diamante, pão, espada)"
            className="w-72 rounded-md border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-sm outline-none focus:border-ouro" />
          <select value={mod} onChange={(e) => setMod(e.target.value)} className="rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1.5 text-xs">
            <option value="todos">Todos os mods</option>
            {mods.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <button onClick={aoFechar} className="ml-auto rounded-md border border-pedra-700 px-3 py-1 text-sm hover:border-ouro">Fechar</button>
        </div>
        <div className="overflow-y-auto p-4">
          {todos === undefined && <p className="text-sm text-pedra-500">Carregando itens…</p>}
          {todos === null && <p className="text-sm text-yellow-200">A lista de itens chega quando o servidor ligar com o core mod 0.34 ou mais novo. Até lá, digite o id do item.</p>}
          <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-4">
            {filtrados.slice(0, LIMITE).map((i) => (
              <button key={i.id} onClick={() => { aoEscolher(i.id); aoFechar(); }} title={i.id}
                className="flex items-center gap-2 rounded-lg border border-pedra-700 bg-pedra-950/50 p-1.5 text-left hover:border-ouro/60">
                <IconeItem id={i.id} tam={28} />
                <span className="min-w-0">
                  <span className="block truncate text-sm">{i.nome}</span>
                  <span className="block truncate text-[10px] text-pedra-500">{i.id}</span>
                </span>
              </button>
            ))}
          </div>
          {filtrados.length > LIMITE && <p className="mt-3 text-center text-xs text-pedra-500">Mostrando {LIMITE} de {filtrados.length}. Digite mais para filtrar.</p>}
        </div>
      </div>
    </div>
  );
}

/** Botão que mostra o item escolhido e abre o seletor. */
export function CampoItem({ valor, onChange, titulo = "Escolher item" }: { valor: string; onChange: (id: string) => void; titulo?: string }) {
  const [abrir, setAbrir] = useState(false);
  const mapa = useMapaItens();
  const [texto, setTexto] = useState(false);
  return (
    <div className="flex items-center gap-2">
      <button type="button" onClick={() => setAbrir(true)} className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-pedra-700 bg-pedra-950 px-2.5 py-1.5 text-left text-sm hover:border-ouro">
        {valor ? <IconeItem id={valor} tam={24} /> : <span className="grid h-6 w-6 place-items-center rounded bg-pedra-800">?</span>}
        <span className="min-w-0 flex-1 truncate">{valor ? <NomeItem id={valor} mapa={mapa} /> : "Escolher…"}</span>
        <span className="text-xs text-pedra-500">trocar</span>
      </button>
      <button type="button" onClick={() => setTexto((v) => !v)} title="Digitar o id" className="rounded-md border border-pedra-700 px-2 py-1.5 text-xs text-pedra-500 hover:text-pergaminho">id</button>
      {texto && <input value={valor} onChange={(e) => onChange(e.target.value.trim().toLowerCase())} className="w-56 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 font-mono text-xs" />}
      {abrir && <SeletorItem titulo={titulo} aoEscolher={onChange} aoFechar={() => setAbrir(false)} />}
    </div>
  );
}

// ------------------------------------------------------------------ moedas

const COR_MOEDA: Record<string, [string, string]> = { ouro: ["#ffd84a", "#b8860b"], prata: ["#eef2f5", "#8a96a3"], cobre: ["#e8955a", "#8c4a1f"] };

/** Ícone da moeda do mod; se a textura não carregar, desenha uma moedinha. */
export function IconeMoeda({ tipo, tam = 16 }: { tipo: "ouro" | "prata" | "cobre"; tam?: number }) {
  const [a, b] = COR_MOEDA[tipo];
  const reserva = <span className="inline-block shrink-0 rounded-full" title={tipo} style={{ width: tam * 0.8, height: tam * 0.8, margin: tam * 0.1, background: `radial-gradient(circle at 35% 35%, ${a}, ${b})`, boxShadow: `inset 0 0 0 1px ${b}` }} />;
  return <IconeItem id={`frxworld:moeda_${tipo}`} tam={tam} reserva={reserva} />;
}

export function Moedas({ cobre, tam = 16, className = "" }: { cobre: number; tam?: number; className?: string }) {
  const o = Math.floor(cobre / 100), p = Math.floor((cobre % 100) / 10), c = cobre % 10;
  const parte = (n: number, t: "ouro" | "prata") => n > 0 && <span className="inline-flex items-center gap-0.5"><b className="font-mono">{n.toLocaleString("pt-BR")}</b><IconeMoeda tipo={t} tam={tam} /></span>;
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      {parte(o, "ouro")}{parte(p, "prata")}{(c > 0 || cobre === 0) && <span className="inline-flex items-center gap-0.5"><b className="font-mono">{c}</b><IconeMoeda tipo="cobre" tam={tam} /></span>}
    </span>
  );
}

/** Três campos (ouro, prata, cobre) que viram um valor em cobre. */
export function CampoMoedas({ cobre, onChange }: { cobre: number; onChange: (c: number) => void }) {
  const o = Math.floor(cobre / 100), p = Math.floor((cobre % 100) / 10), c = cobre % 10;
  const set = (no: number, np: number, nc: number) => onChange(Math.max(0, no) * 100 + Math.max(0, np) * 10 + Math.max(0, nc));
  const campo = (v: number, f: (n: number) => void, t: "ouro" | "prata" | "cobre", max?: number) => (
    <span className="flex items-center gap-1 rounded-md border border-pedra-700 bg-pedra-950 pl-1.5 pr-1">
      <IconeMoeda tipo={t} tam={16} />
      <input type="number" min={0} max={max} value={v} onChange={(e) => f(Number(e.target.value) || 0)} className="w-14 bg-transparent py-1 text-right font-mono text-sm outline-none" />
    </span>
  );
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      {campo(o, (n) => set(n, p, c), "ouro")}
      {campo(p, (n) => set(o, Math.min(9, n), c), "prata", 9)}
      {campo(c, (n) => set(o, p, Math.min(9, n)), "cobre", 9)}
    </span>
  );
}
