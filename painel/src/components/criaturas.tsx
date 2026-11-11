"use client";

import { useEffect, useMemo, useState } from "react";

/** Criaturas do modpack (lista mandada pelo mod ao ligar) com ícone de ovo colorido e seletor com busca. */

export type Criatura = { id: string; nome: string; mod: string; categoria: string; chefe: boolean; cor1?: number; cor2?: number };

let lista: Promise<Criatura[] | null> | null = null;
function carregarLista() {
  lista ??= fetch("/api/painel/entidades", { cache: "no-store" }).then((r) => r.json()).then((j) => j.criaturas ?? null).catch(() => null);
  return lista;
}

export function useCriaturas() {
  const [c, setC] = useState<Criatura[] | null | undefined>(undefined);
  useEffect(() => { let vivo = true; carregarLista().then((x) => { if (vivo) setC(x); }); return () => { vivo = false; }; }, []);
  return c;
}

// ------------------------------------------------------------------ ovo colorido (texturas do próprio jogo)

let texturas: Promise<[HTMLImageElement, HTMLImageElement] | null> | null = null;
function carregarTexturas() {
  const um = (p: string) => new Promise<HTMLImageElement>((ok, falha) => {
    const im = new Image(); im.onload = () => ok(im); im.onerror = falha;
    im.src = `/api/painel/textura?p=${encodeURIComponent(p)}`;
  });
  texturas ??= Promise.all([um("minecraft/textures/item/spawn_egg.png"), um("minecraft/textures/item/spawn_egg_overlay.png")]).catch(() => null);
  return texturas;
}

const ovos = new Map<string, string>();
function tingir(im: HTMLImageElement, cor: number) {
  const c = document.createElement("canvas"); c.width = 16; c.height = 16;
  const x = c.getContext("2d")!;
  x.drawImage(im, 0, 0, 16, 16);
  const d = x.getImageData(0, 0, 16, 16);
  const r = (cor >> 16) & 255, g = (cor >> 8) & 255, b = cor & 255;
  for (let i = 0; i < d.data.length; i += 4) { d.data[i] = (d.data[i] * r) / 255; d.data[i + 1] = (d.data[i + 1] * g) / 255; d.data[i + 2] = (d.data[i + 2] * b) / 255; }
  x.putImageData(d, 0, 0);
  return c;
}

async function ovo(cor1: number, cor2: number) {
  const k = `${cor1}-${cor2}`;
  if (ovos.has(k)) return ovos.get(k)!;
  const t = await carregarTexturas();
  if (!t) return null;
  const c = document.createElement("canvas"); c.width = 16; c.height = 16;
  const x = c.getContext("2d")!;
  x.drawImage(tingir(t[0], cor1), 0, 0);
  x.drawImage(tingir(t[1], cor2), 0, 0);
  const url = c.toDataURL();
  ovos.set(k, url);
  return url;
}

export function IconeCriatura({ c, id, tam = 24 }: { c?: Criatura; id: string; tam?: number }) {
  const [url, setUrl] = useState<string | null>(null);
  const [falhou, setFalhou] = useState(false);
  useEffect(() => {
    let vivo = true;
    if (c?.cor1 !== undefined && c.cor2 !== undefined) ovo(c.cor1, c.cor2).then((u) => { if (vivo) setUrl(u); });
    return () => { vivo = false; };
  }, [c?.cor1, c?.cor2]);
  const fallback = !c || c.cor1 === undefined;
  if (fallback && !falhou) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={`/api/painel/item-icone?id=${encodeURIComponent(`${id}_spawn_egg`)}`} alt="" width={tam} height={tam} onError={() => setFalhou(true)} className="shrink-0 [image-rendering:pixelated]" />;
  }
  if (!url) {
    const nome = id.split(":")[1] ?? id;
    return <span className="grid shrink-0 place-items-center rounded bg-pedra-700 text-[9px] font-bold uppercase" style={{ width: tam, height: tam }}>{c?.chefe ? "☠" : nome.slice(0, 2)}</span>;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={url} alt="" width={tam} height={tam} className="shrink-0 [image-rendering:pixelated]" />;
}

// ------------------------------------------------------------------ seletor

const FILTROS: { id: string; nome: string; f: (c: Criatura) => boolean }[] = [
  { id: "monstros", nome: "👾 Monstros", f: (c) => c.categoria === "monster" && !c.chefe },
  { id: "chefes", nome: "☠️ Chefes", f: (c) => c.chefe },
  { id: "animais", nome: "🐾 Animais", f: (c) => ["creature", "ambient", "water_creature", "water_ambient", "underground_water_creature", "axolotls"].includes(c.categoria) },
  { id: "outros", nome: "✳️ Outros", f: (c) => c.categoria === "misc" && !c.chefe },
  { id: "todos", nome: "Todos", f: () => true },
];

export function SeletorCriaturas({ titulo, selecionadas, aoMudar, aoFechar, filtroInicial = "monstros", unico = false }: {
  titulo: string; selecionadas: string[]; aoMudar: (ids: string[]) => void; aoFechar: () => void; filtroInicial?: string; unico?: boolean;
}) {
  const todas = useCriaturas();
  const [busca, setBusca] = useState("");
  const [filtro, setFiltro] = useState(filtroInicial);
  const [mod, setMod] = useState("todos");
  const [soMarcadas, setSoMarcadas] = useState(false);

  const mods = useMemo(() => [...new Set((todas ?? []).map((c) => c.mod))].sort(), [todas]);
  const visiveis = useMemo(() => {
    const q = busca.trim().toLowerCase();
    const f = FILTROS.find((x) => x.id === filtro)?.f ?? (() => true);
    return (todas ?? []).filter((c) => (soMarcadas ? selecionadas.includes(c.id) : f(c)) && (mod === "todos" || c.mod === mod)
      && (!q || c.nome.toLowerCase().includes(q) || c.id.includes(q)));
  }, [todas, busca, filtro, mod, soMarcadas, selecionadas]);

  const alternar = (id: string) => {
    if (unico) { aoMudar([id]); aoFechar(); return; }
    aoMudar(selecionadas.includes(id) ? selecionadas.filter((x) => x !== id) : [...selecionadas, id]);
  };

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4" onClick={aoFechar}>
      <div className="flex max-h-[90vh] w-full max-w-5xl flex-col rounded-2xl border border-ouro/30 bg-pedra-900 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="border-b border-pedra-700 p-4">
          <div className="flex items-center gap-3">
            <h3 className="font-titulo text-xl text-ouro">{titulo}</h3>
            {!unico && <span className="rounded-full bg-ouro/15 px-2 py-0.5 text-xs text-ouro">{selecionadas.length} escolhidos</span>}
            <button onClick={aoFechar} className="ml-auto rounded-md bg-ouro px-4 py-1 text-sm font-semibold text-pedra-950">Pronto</button>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <input autoFocus value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="🔎 Buscar pelo nome… (ex.: zumbi, aranha, dragão)"
              className="w-72 rounded-md border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-sm outline-none focus:border-ouro" />
            {FILTROS.map((f) => (
              <button key={f.id} onClick={() => { setFiltro(f.id); setSoMarcadas(false); }}
                className={`rounded-md border px-2.5 py-1 text-xs ${filtro === f.id && !soMarcadas ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`}>{f.nome}</button>
            ))}
            {!unico && <button onClick={() => setSoMarcadas((v) => !v)} className={`rounded-md border px-2.5 py-1 text-xs ${soMarcadas ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500"}`}>✓ Só os escolhidos</button>}
            <select value={mod} onChange={(e) => setMod(e.target.value)} className="ml-auto rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-xs">
              <option value="todos">Todos os mods</option>
              {mods.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>
        </div>
        <div className="overflow-y-auto p-4">
          {todas === undefined && <p className="text-sm text-pedra-500">Carregando criaturas…</p>}
          {todas === null && <p className="text-sm text-yellow-200">A lista de criaturas chega quando o servidor ligar com o core mod 0.32 ou mais novo. Até lá, digite o id à mão.</p>}
          {todas && !visiveis.length && <p className="text-sm text-pedra-500">Nada encontrado.</p>}
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
            {visiveis.map((c) => {
              const marcada = selecionadas.includes(c.id);
              return (
                <button key={c.id} onClick={() => alternar(c.id)} title={c.id}
                  className={`flex items-center gap-2.5 rounded-lg border p-2 text-left transition ${marcada ? "border-ouro bg-ouro/15" : "border-pedra-700 bg-pedra-950/50 hover:border-ouro/50"}`}>
                  <IconeCriatura c={c} id={c.id} tam={32} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm">{c.chefe && "☠ "}{c.nome}</span>
                    <span className="block truncate text-[11px] text-pedra-500">{c.mod}</span>
                  </span>
                  <span className={`grid h-5 w-5 shrink-0 place-items-center rounded border text-xs ${marcada ? "border-ouro bg-ouro text-pedra-950" : "border-pedra-700"}`}>{marcada ? "✓" : ""}</span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
