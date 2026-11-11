"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BonecoCanvas } from "@/components/boneco-canvas";

/**
 * Editor de skin em pixel art (64×64, formato do Minecraft) com guia das partes do corpo,
 * ferramentas de desenho e prévia 3D ao vivo.
 */

type Ferramenta = "lapis" | "borracha" | "balde" | "conta" | "escurecer" | "clarear";
type Regiao = { nome: string; x: number; y: number; w: number; h: number; segunda: boolean };

const PARTES = [
  { nome: "Cabeça", u: 0, v: 0, w: 8, h: 8, d: 8, s: false },
  { nome: "Chapéu", u: 32, v: 0, w: 8, h: 8, d: 8, s: true },
  { nome: "Tronco", u: 16, v: 16, w: 8, h: 12, d: 4, s: false },
  { nome: "Jaqueta", u: 16, v: 32, w: 8, h: 12, d: 4, s: true },
  { nome: "Braço direito", u: 40, v: 16, w: 4, h: 12, d: 4, s: false, braco: true },
  { nome: "Manga direita", u: 40, v: 32, w: 4, h: 12, d: 4, s: true, braco: true },
  { nome: "Braço esquerdo", u: 32, v: 48, w: 4, h: 12, d: 4, s: false, braco: true },
  { nome: "Manga esquerda", u: 48, v: 48, w: 4, h: 12, d: 4, s: true, braco: true },
  { nome: "Perna direita", u: 0, v: 16, w: 4, h: 12, d: 4, s: false },
  { nome: "Calça direita", u: 0, v: 32, w: 4, h: 12, d: 4, s: true },
  { nome: "Perna esquerda", u: 16, v: 48, w: 4, h: 12, d: 4, s: false },
  { nome: "Calça esquerda", u: 0, v: 48, w: 4, h: 12, d: 4, s: true },
];

function regioes(slim: boolean): Regiao[] {
  const r: Regiao[] = [];
  for (const p of PARTES) {
    const w = p.braco && slim ? 3 : p.w, { u, v, h, d } = p;
    const faces: [string, number, number, number, number][] = [
      ["topo", u + d, v, w, d], ["baixo", u + d + w, v, w, d],
      ["lado direito", u, v + d, d, h], ["frente", u + d, v + d, w, h],
      ["lado esquerdo", u + d + w, v + d, d, h], ["costas", u + 2 * d + w, v + d, w, h],
    ];
    for (const [f, x, y, fw, fh] of faces) r.push({ nome: `${p.nome}${p.s ? " (2ª camada)" : ""} · ${f}`, x, y, w: fw, h: fh, segunda: p.s });
  }
  return r;
}

/** Copiar partes de um lado para o outro (mesma posição relativa). */
const PARES: [number, number, number, number][] = [
  [40, 16, 32, 48], [40, 32, 48, 48], [0, 16, 16, 48], [0, 32, 0, 48],
];

const PALETA = [
  "#000000", "#1d1d21", "#3c3c44", "#6b6b75", "#a0a0aa", "#d4d4dc", "#ffffff",
  "#5b3a29", "#8a5a3c", "#b8835a", "#e0b48a", "#f3d3b3", "#ffe6cc",
  "#7a1e1e", "#c0392b", "#e74c3c", "#ff8a65", "#e67e22", "#f1c40f", "#fff176",
  "#1e5631", "#27ae60", "#7cd67c", "#16a085", "#0e3a5e", "#2980b9", "#5dade2", "#a6e1fa",
  "#3d1e6d", "#8e44ad", "#c58cf0", "#d81b60", "#f48fb1", "#d4a93a", "#9c7a22",
];

const hex = (r: number, g: number, b: number) => `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
const rgb = (h: string): [number, number, number] => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

export function EditorSkin({ titulo, inicial, slimInicial, bases, aoSalvar, aoFechar }: {
  titulo: string;
  inicial: string | null; // URL da skin atual (ou null)
  slimInicial: boolean;
  bases: { nome: string; url: string }[];
  aoSalvar: (png: string, slim: boolean) => Promise<string | null>; // devolve erro ou null
  aoFechar: () => void;
}) {
  const dados = useRef<ImageData>(new ImageData(64, 64));
  const tela = useRef<HTMLCanvasElement>(null);
  const [zoom, setZoom] = useState(9);
  const [ferr, setFerr] = useState<Ferramenta>("lapis");
  const [cor, setCor] = useState("#c0392b");
  const [recentes, setRecentes] = useState<string[]>([]);
  const [tam, setTam] = useState(1);
  const [slim, setSlim] = useState(slimInicial);
  const [grade, setGrade] = useState(true);
  const [guia, setGuia] = useState(true);
  const [espelho, setEspelho] = useState(false);
  const [limitarFace, setLimitarFace] = useState(true);
  const [mouse, setMouse] = useState<{ x: number; y: number } | null>(null);
  const [previa, setPrevia] = useState<string | null>(null);
  const [versao, setVersao] = useState(0);
  const [mudou, setMudou] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const desfazer = useRef<ImageData[]>([]);
  const refazer = useRef<ImageData[]>([]);
  const pintando = useRef(false);
  const arquivo = useRef<HTMLInputElement>(null);

  const regs = useMemo(() => regioes(slim), [slim]);
  const regiaoEm = useCallback((x: number, y: number) => regs.find((r) => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h) ?? null, [regs]);

  const gerarPng = useCallback(() => {
    const c = document.createElement("canvas");
    c.width = 64; c.height = 64;
    c.getContext("2d")!.putImageData(dados.current, 0, 0);
    return c.toDataURL("image/png");
  }, []);

  const atualizarPrevia = useCallback(() => setPrevia(gerarPng()), [gerarPng]);

  /** Carrega uma imagem (64×64 ou 64×32 antiga) como ponto de partida. */
  const carregarUrl = useCallback((url: string | null) => {
    if (!url) {
      dados.current = new ImageData(64, 64);
      setVersao((v) => v + 1); atualizarPrevia();
      return;
    }
    const im = new Image();
    im.onload = () => {
      const c = document.createElement("canvas");
      c.width = 64; c.height = 64;
      const x = c.getContext("2d")!;
      x.imageSmoothingEnabled = false;
      const alturaFonte = Math.round((64 * im.naturalHeight) / im.naturalWidth);
      x.drawImage(im, 0, 0, 64, alturaFonte);
      if (alturaFonte < 64) { // skin antiga 64×32: copia braço e perna para o lado esquerdo
        x.drawImage(c, 0, 16, 16, 16, 16, 48, 16, 16);
        x.drawImage(c, 40, 16, 16, 16, 32, 48, 16, 16);
      }
      dados.current = x.getImageData(0, 0, 64, 64);
      setVersao((v) => v + 1); atualizarPrevia();
    };
    im.onerror = () => setMsg("Não consegui abrir essa imagem.");
    im.src = url;
  }, [atualizarPrevia]);

  // carrega o ponto de partida só ao abrir (não a cada renderização do pai)
  const partida = useRef(inicial ?? bases[0]?.url ?? null);
  useEffect(() => {
    carregarUrl(partida.current);
  }, [carregarUrl]);

  // desenho da tela do editor
  useEffect(() => {
    const cv = tela.current;
    if (!cv) return;
    const L = 64 * zoom;
    cv.width = L; cv.height = L;
    const ctx = cv.getContext("2d")!;
    ctx.imageSmoothingEnabled = false;
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      ctx.fillStyle = (x + y) % 2 ? "#3a3530" : "#2c2824";
      ctx.fillRect(x * zoom, y * zoom, zoom, zoom);
    }
    // áreas que não são usadas pela skin ficam mais escuras
    ctx.fillStyle = "rgba(0,0,0,0.55)";
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (!regiaoEm(x, y)) ctx.fillRect(x * zoom, y * zoom, zoom, zoom);
    const tmp = document.createElement("canvas");
    tmp.width = 64; tmp.height = 64;
    tmp.getContext("2d")!.putImageData(dados.current, 0, 0);
    ctx.drawImage(tmp, 0, 0, L, L);
    if (grade && zoom >= 6) {
      ctx.strokeStyle = "rgba(255,255,255,0.06)"; ctx.lineWidth = 1;
      for (let i = 0; i <= 64; i++) { ctx.beginPath(); ctx.moveTo(i * zoom + 0.5, 0); ctx.lineTo(i * zoom + 0.5, L); ctx.stroke(); ctx.beginPath(); ctx.moveTo(0, i * zoom + 0.5); ctx.lineTo(L, i * zoom + 0.5); ctx.stroke(); }
    }
    if (guia) {
      for (const r of regs) {
        ctx.strokeStyle = r.segunda ? "rgba(197,140,240,0.65)" : "rgba(212,169,58,0.7)";
        ctx.lineWidth = 1;
        ctx.strokeRect(r.x * zoom + 0.5, r.y * zoom + 0.5, r.w * zoom - 1, r.h * zoom - 1);
      }
    }
    if (mouse) {
      const r = regiaoEm(mouse.x, mouse.y);
      if (r) { ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 2; ctx.strokeRect(r.x * zoom + 1, r.y * zoom + 1, r.w * zoom - 2, r.h * zoom - 2); }
      ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 1;
      const o = Math.floor((tam - 1) / 2);
      ctx.strokeRect((mouse.x - o) * zoom + 0.5, (mouse.y - o) * zoom + 0.5, tam * zoom - 1, tam * zoom - 1);
    }
  }, [zoom, grade, guia, regs, mouse, versao, regiaoEm, tam]);

  const guardarDesfazer = () => {
    desfazer.current.push(new ImageData(new Uint8ClampedArray(dados.current.data), 64, 64));
    if (desfazer.current.length > 60) desfazer.current.shift();
    refazer.current = [];
  };

  const px = (x: number, y: number) => (y * 64 + x) * 4;

  const pintar = (x: number, y: number, origem: Regiao | null) => {
    const d = dados.current.data;
    const o = Math.floor((tam - 1) / 2);
    const alvos: [number, number][] = [];
    for (let dy = 0; dy < tam; dy++) for (let dx = 0; dx < tam; dx++) alvos.push([x - o + dx, y - o + dy]);
    if (espelho) {
      for (const [a, b] of [...alvos]) {
        for (const [ux, uy, vx, vy] of PARES) {
          if (a >= ux && a < ux + 16 && b >= uy && b < uy + 16) alvos.push([a - ux + vx, b - uy + vy]);
          else if (a >= vx && a < vx + 16 && b >= vy && b < vy + 16) alvos.push([a - vx + ux, b - vy + uy]);
        }
      }
    }
    const [r, g, b] = rgb(cor);
    for (const [ax, ay] of alvos) {
      if (ax < 0 || ay < 0 || ax >= 64 || ay >= 64) continue;
      if (limitarFace && origem && !espelho && !(ax >= origem.x && ax < origem.x + origem.w && ay >= origem.y && ay < origem.y + origem.h)) continue;
      const i = px(ax, ay);
      if (ferr === "lapis") { d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = 255; }
      else if (ferr === "borracha") { d[i + 3] = 0; }
      else if (ferr === "escurecer" || ferr === "clarear") {
        if (!d[i + 3]) continue;
        const f = ferr === "escurecer" ? 0.85 : 1.15;
        d[i] = Math.min(255, d[i] * f + (f > 1 ? 6 : 0)); d[i + 1] = Math.min(255, d[i + 1] * f + (f > 1 ? 6 : 0)); d[i + 2] = Math.min(255, d[i + 2] * f + (f > 1 ? 6 : 0));
      }
    }
  };

  const balde = (x: number, y: number) => {
    const d = dados.current.data;
    const r0 = regiaoEm(x, y);
    const dentro = (a: number, b: number) => a >= 0 && b >= 0 && a < 64 && b < 64 && (!limitarFace || !r0 || (a >= r0.x && a < r0.x + r0.w && b >= r0.y && b < r0.y + r0.h));
    const i0 = px(x, y);
    const alvo = [d[i0], d[i0 + 1], d[i0 + 2], d[i0 + 3]];
    const [r, g, b] = rgb(cor);
    if (alvo[3] === 255 && alvo[0] === r && alvo[1] === g && alvo[2] === b) return;
    const igual = (i: number) => d[i] === alvo[0] && d[i + 1] === alvo[1] && d[i + 2] === alvo[2] && d[i + 3] === alvo[3];
    const pilha: [number, number][] = [[x, y]];
    const visto = new Set<number>();
    while (pilha.length) {
      const [a, bb] = pilha.pop()!;
      if (!dentro(a, bb)) continue;
      const k = bb * 64 + a;
      if (visto.has(k)) continue;
      visto.add(k);
      const i = k * 4;
      if (!igual(i)) continue;
      d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = 255;
      pilha.push([a + 1, bb], [a - 1, bb], [a, bb + 1], [a, bb - 1]);
    }
  };

  const posicao = (e: React.MouseEvent) => {
    const rect = (e.target as HTMLCanvasElement).getBoundingClientRect();
    return { x: Math.floor(((e.clientX - rect.left) / rect.width) * 64), y: Math.floor(((e.clientY - rect.top) / rect.height) * 64) };
  };

  const usarCor = (c: string) => { setCor(c); setRecentes((r) => [c, ...r.filter((x) => x !== c)].slice(0, 12)); };

  const acao = (e: React.MouseEvent, inicio: boolean) => {
    const { x, y } = posicao(e);
    if (x < 0 || y < 0 || x >= 64 || y >= 64) return;
    if (ferr === "conta") {
      const i = px(x, y), d = dados.current.data;
      if (d[i + 3]) { usarCor(hex(d[i], d[i + 1], d[i + 2])); setFerr("lapis"); }
      return;
    }
    if (inicio) guardarDesfazer();
    if (ferr === "balde") { if (inicio) balde(x, y); }
    else pintar(x, y, regiaoEm(x, y));
    setMudou(true);
    setVersao((v) => v + 1);
  };

  const voltar = () => {
    const a = desfazer.current.pop();
    if (!a) return;
    refazer.current.push(new ImageData(new Uint8ClampedArray(dados.current.data), 64, 64));
    dados.current = a; setVersao((v) => v + 1); atualizarPrevia();
  };
  const avancar = () => {
    const a = refazer.current.pop();
    if (!a) return;
    desfazer.current.push(new ImageData(new Uint8ClampedArray(dados.current.data), 64, 64));
    dados.current = a; setVersao((v) => v + 1); atualizarPrevia();
  };

  const copiarLado = (deDireita: boolean) => {
    guardarDesfazer();
    const c = document.createElement("canvas"); c.width = 64; c.height = 64;
    const x = c.getContext("2d")!; x.putImageData(dados.current, 0, 0);
    for (const [ux, uy, vx, vy] of PARES) {
      const [sx, sy, tx, ty] = deDireita ? [ux, uy, vx, vy] : [vx, vy, ux, uy];
      const img = x.getImageData(sx, sy, 16, 16);
      x.clearRect(tx, ty, 16, 16);
      x.putImageData(img, tx, ty);
    }
    dados.current = x.getImageData(0, 0, 64, 64);
    setMudou(true); setVersao((v) => v + 1); atualizarPrevia();
  };

  // atalhos
  useEffect(() => {
    const t = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === "INPUT") return;
      if (e.ctrlKey && e.key.toLowerCase() === "z") { e.preventDefault(); voltar(); }
      else if (e.ctrlKey && e.key.toLowerCase() === "y") { e.preventDefault(); avancar(); }
      else if (e.key === "b") setFerr("lapis");
      else if (e.key === "e") setFerr("borracha");
      else if (e.key === "g") setFerr("balde");
      else if (e.key === "i") setFerr("conta");
    };
    window.addEventListener("keydown", t);
    return () => window.removeEventListener("keydown", t);
  });

  const salvar = async () => {
    setSalvando(true); setMsg(null);
    const erro = await aoSalvar(gerarPng(), slim);
    setSalvando(false);
    if (erro) setMsg(erro); else { setMudou(false); setMsg("Salvo! Os jogadores recebem em até 30 s."); }
  };

  const baixar = () => {
    const a = document.createElement("a");
    a.href = gerarPng(); a.download = `${titulo.replace(/[^\w-]+/g, "_")}.png`; a.click();
  };

  const regAtual = mouse ? regiaoEm(mouse.x, mouse.y) : null;
  const ferramentas: [Ferramenta, string, string][] = [
    ["lapis", "✏️", "Lápis (B)"], ["borracha", "🧽", "Borracha (E)"], ["balde", "🪣", "Balde de tinta (G)"],
    ["conta", "💧", "Conta-gotas (I)"], ["escurecer", "🌑", "Escurecer"], ["clarear", "🌕", "Clarear"],
  ];
  const btn = (ativo: boolean) => `rounded-md border px-2 py-1 text-sm ${ativo ? "border-ouro bg-ouro/20 text-ouro" : "border-pedra-700 hover:border-ouro/60"}`;

  return (
    <div className="fixed inset-0 z-50 overflow-auto bg-black/80 p-4" onMouseUp={() => { if (pintando.current) { pintando.current = false; atualizarPrevia(); } }}>
      <div className="mx-auto max-w-[1400px] rounded-2xl border border-ouro/30 bg-pedra-900 p-4 shadow-2xl">
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <h2 className="font-titulo text-2xl text-ouro">🎨 {titulo}</h2>
          {mudou && <span className="rounded-full bg-yellow-500/20 px-2 py-0.5 text-xs text-yellow-200">alterações não salvas</span>}
          <div className="ml-auto flex flex-wrap gap-2">
            <button onClick={() => arquivo.current?.click()} className={btn(false)}>📂 Importar PNG</button>
            <button onClick={baixar} className={btn(false)}>💾 Baixar PNG</button>
            <button disabled={salvando} onClick={salvar} className="rounded-md bg-ouro px-4 py-1 text-sm font-semibold text-pedra-950 hover:bg-ouro/90 disabled:opacity-50">{salvando ? "Salvando…" : "Salvar skin"}</button>
            <button onClick={() => { if (!mudou || confirm("Fechar sem salvar?")) aoFechar(); }} className={btn(false)}>✕ Fechar</button>
          </div>
          <input ref={arquivo} type="file" accept="image/png" className="hidden" onChange={(e) => {
            const f = e.target.files?.[0];
            if (!f) return;
            guardarDesfazer();
            carregarUrl(URL.createObjectURL(f));
            setMudou(true);
            e.target.value = "";
          }} />
        </div>
        {msg && <p className={`mb-3 rounded-lg px-3 py-2 text-sm ${msg.startsWith("Salvo") ? "bg-green-950/50 text-green-200" : "bg-red-950/50 text-red-200"}`}>{msg}</p>}

        <div className="grid gap-4 xl:grid-cols-[auto_1fr_auto]">
          {/* ferramentas */}
          <div className="flex w-56 flex-col gap-3">
            <div>
              <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Ferramenta</p>
              <div className="grid grid-cols-3 gap-1">
                {ferramentas.map(([id, ic, nome]) => <button key={id} title={nome} onClick={() => setFerr(id)} className={btn(ferr === id)}>{ic}</button>)}
              </div>
              <p className="mt-1 text-xs text-pedra-500">{ferramentas.find((f) => f[0] === ferr)?.[2]}</p>
            </div>
            <div>
              <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Tamanho do pincel</p>
              <div className="flex gap-1">{[1, 2, 3].map((n) => <button key={n} onClick={() => setTam(n)} className={btn(tam === n)}>{n}px</button>)}</div>
            </div>
            <div>
              <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Cor</p>
              <div className="flex items-center gap-2">
                <input type="color" value={cor} onChange={(e) => usarCor(e.target.value)} className="h-9 w-12 cursor-pointer rounded border border-pedra-700 bg-transparent" />
                <span className="font-mono text-sm">{cor}</span>
              </div>
              <div className="mt-2 grid grid-cols-7 gap-1">
                {PALETA.map((c) => <button key={c} title={c} onClick={() => usarCor(c)} className={`h-6 w-6 rounded-sm border ${cor === c ? "border-white ring-1 ring-white" : "border-black/40"}`} style={{ background: c }} />)}
              </div>
              {!!recentes.length && <>
                <p className="mb-1 mt-2 text-xs text-pedra-500">Usadas agora</p>
                <div className="flex flex-wrap gap-1">{recentes.map((c) => <button key={c} onClick={() => setCor(c)} className="h-5 w-5 rounded-sm border border-black/40" style={{ background: c }} />)}</div>
              </>}
            </div>
            <div className="space-y-1.5 text-sm">
              <label className="flex items-center gap-2"><input type="checkbox" checked={limitarFace} onChange={(e) => setLimitarFace(e.target.checked)} /> Pintar só dentro da face</label>
              <label className="flex items-center gap-2"><input type="checkbox" checked={espelho} onChange={(e) => setEspelho(e.target.checked)} /> Braços e pernas iguais dos dois lados</label>
              <label className="flex items-center gap-2"><input type="checkbox" checked={guia} onChange={(e) => setGuia(e.target.checked)} /> Mostrar guia das partes</label>
              <label className="flex items-center gap-2"><input type="checkbox" checked={grade} onChange={(e) => setGrade(e.target.checked)} /> Grade</label>
              <label className="flex items-center gap-2"><input type="checkbox" checked={slim} onChange={(e) => { setSlim(e.target.checked); setMudou(true); }} /> Braços finos (modelo Alex)</label>
            </div>
            <div className="flex flex-wrap gap-1">
              <button onClick={voltar} className={btn(false)} title="Ctrl+Z">↶ Desfazer</button>
              <button onClick={avancar} className={btn(false)} title="Ctrl+Y">↷ Refazer</button>
              <button onClick={() => copiarLado(true)} className={btn(false)} title="Copia braço e perna direitos para o esquerdo">⇉ Direito → esquerdo</button>
              <button onClick={() => copiarLado(false)} className={btn(false)}>⇇ Esquerdo → direito</button>
            </div>
            <div>
              <p className="mb-1 text-xs uppercase tracking-wider text-pedra-500">Começar de</p>
              <div className="flex flex-wrap gap-1">
                {bases.map((b) => <button key={b.nome} className={btn(false)} onClick={() => { if (!mudou || confirm("Trocar e perder o desenho atual?")) { guardarDesfazer(); carregarUrl(b.url); setMudou(true); } }}>{b.nome}</button>)}
                <button className={btn(false)} onClick={() => { if (!mudou || confirm("Apagar tudo?")) { guardarDesfazer(); carregarUrl(null); setMudou(true); } }}>Em branco</button>
              </div>
            </div>
          </div>

          {/* tela */}
          <div className="min-w-0">
            <div className="mb-2 flex items-center gap-2 text-sm">
              <span className="text-pedra-500">Zoom</span>
              <input type="range" min={5} max={14} value={zoom} onChange={(e) => setZoom(Number(e.target.value))} />
              <span className="ml-auto font-mono text-xs text-pedra-500">{mouse ? `x ${mouse.x} · y ${mouse.y}` : ""} {regAtual ? `· ${regAtual.nome}` : mouse ? "· (não usado)" : ""}</span>
            </div>
            <div className="overflow-auto rounded-lg border border-pedra-700">
              <canvas
                ref={tela}
                className={`block ${ferr === "conta" ? "cursor-copy" : "cursor-crosshair"}`}
                style={{ width: 64 * zoom, height: 64 * zoom }}
                onMouseDown={(e) => { pintando.current = true; acao(e, true); }}
                onMouseMove={(e) => { const p = posicao(e); setMouse(p); if (pintando.current && ferr !== "balde" && ferr !== "conta") acao(e, false); }}
                onMouseLeave={() => setMouse(null)}
                onContextMenu={(e) => e.preventDefault()}
              />
            </div>
            <p className="mt-2 text-xs text-pedra-500">
              Contornos <span className="text-ouro">dourados</span> = corpo · <span className="text-purple-300">roxos</span> = 2ª camada (roupa/cabelo por cima, pode ter transparência).
              Áreas escuras não aparecem no jogo. No Minecraft, pixel transparente na camada de baixo vira <b>preto</b>.
            </p>
          </div>

          {/* prévia */}
          <div className="flex flex-col items-center gap-2">
            <p className="text-xs uppercase tracking-wider text-pedra-500">Prévia</p>
            <div className="rounded-lg border border-pedra-700" style={{ background: "linear-gradient(#7fb6ec 0%, #bfe1f7 70%, #5d9c3a 70%, #4c7f2f 100%)" }}>
              <BonecoCanvas sk={previa ? { url: previa, slim, altura: 64 } : null} largura={260} alturaPx={350} />
            </div>
            <p className="max-w-[260px] text-center text-xs text-pedra-500">A prévia atualiza quando você solta o mouse. Arraste para girar.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
