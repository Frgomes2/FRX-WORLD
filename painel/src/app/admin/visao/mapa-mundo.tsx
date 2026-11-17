"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export type MapaDados = {
  regioes: { rx: number; rz: number; b: string }[];
  chunks: number;
  tamanhoMb: number;
  porDimensao: Record<string, { chunks: number; mb: number }>;
  jm: { pasta: string; camadas: string[]; tiles: Record<string, [number, number][]>; atualizadoEm: number } | null;
};

type Reino = { id: string; nome: string; x0: number; z0: number; x1: number; z1: number; subterraneo: boolean };
type Jog = { nick: string; uuid: string; x: number; z: number };
type Masm = { id: string; nome: string; x: number; z: number; colocada: boolean };
type Chefe = { nome: string; x: number; z: number; fase: string } | null;

const CORES_JOG = ["#4fd1ff", "#ff7ad9", "#7cff6b", "#ffd84a", "#ff8c42", "#b18cff", "#ff5c5c", "#5cffc9"];

/** Imagem dos chunks explorados (1 pixel = 1 chunk). */
function montarImagem(mapa: MapaDados) {
  if (!mapa.regioes.length) return null;
  let rx0 = Infinity, rz0 = Infinity, rx1 = -Infinity, rz1 = -Infinity;
  for (const r of mapa.regioes) { rx0 = Math.min(rx0, r.rx); rz0 = Math.min(rz0, r.rz); rx1 = Math.max(rx1, r.rx); rz1 = Math.max(rz1, r.rz); }
  const w = (rx1 - rx0 + 1) * 32, h = (rz1 - rz0 + 1) * 32;
  const cv = document.createElement("canvas");
  cv.width = w; cv.height = h;
  const ctx = cv.getContext("2d");
  if (!ctx) return null;
  const img = ctx.createImageData(w, h);
  for (const r of mapa.regioes) {
    const bits = Uint8Array.from(atob(r.b), (c) => c.charCodeAt(0));
    const ox = (r.rx - rx0) * 32, oz = (r.rz - rz0) * 32;
    for (let i = 0; i < 1024; i++) {
      if (!(bits[i >> 3] & (1 << (i & 7)))) continue;
      const cx = ox + (i & 31), cz = oz + (i >> 5);
      // tom de "terra/floresta" com um pouco de variação, para parecer mapa
      const ruido = ((cx * 73856093) ^ (cz * 19349663)) & 31;
      const p = (cz * w + cx) * 4;
      img.data[p] = 70 + ruido; img.data[p + 1] = 84 + ruido; img.data[p + 2] = 72 + (ruido >> 1); img.data[p + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return { cv, cx0: rx0 * 32, cz0: rz0 * 32 };
}

export function MapaMundo({ mapa, reinos, jogadores, masmorras, spawn, chefe, foco }: {
  mapa: MapaDados | null; reinos: Reino[]; jogadores: Jog[]; masmorras: Masm[]; spawn: [number, number] | null; chefe: Chefe;
  /** Quando muda (n), o mapa centraliza neste ponto. */
  foco?: { x: number; z: number; esc: number; n: number } | null;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  const caixa = useRef<HTMLDivElement>(null);
  type Vista = { x: number; z: number; esc: number };
  const [vistaUser, setVistaUser] = useState<Vista | null>(null);
  const [mouse, setMouse] = useState<{ x: number; z: number } | null>(null);
  const [tam, setTam] = useState({ w: 800, h: 520 });
  const [camadas, setCamadas] = useState({ jogo: true, explorado: true, reinos: true, masmorras: true, jogadores: true, grade: true });
  const [camadaJm, setCamadaJm] = useState("day");
  const tiles = useRef(new Map<string, HTMLImageElement>());
  const [, setCarregadas] = useState(0);
  const arrasto = useRef<{ x: number; y: number; vx: number; vz: number } | null>(null);
  const [pulso, setPulso] = useState(0);

  const imagem = useMemo(() => (mapa && typeof document !== "undefined" ? montarImagem(mapa) : null), [mapa]);

  // tamanho do canvas acompanha a largura do painel
  useEffect(() => {
    const el = caixa.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setTam({ w: el.clientWidth, h: Math.max(300, Math.min(620, Math.round(el.clientWidth * 0.62))) }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => { const t = setInterval(() => setPulso((p) => p + 1), 120); return () => clearInterval(t); }, []);

  /** Enquadra reinos, jogadores e spawn (tudo = mundo explorado inteiro). */
  const calcular = useCallback((tudo: boolean): Vista => {
    const pts: [number, number][] = [];
    for (const r of reinos) pts.push([r.x0, r.z0], [r.x1, r.z1]);
    for (const j of jogadores) pts.push([j.x, j.z]);
    if (spawn) pts.push(spawn);
    if (tudo || pts.length < 2) {
      for (const m of masmorras) pts.push([m.x, m.z]);
      if (imagem) pts.push([imagem.cx0 * 16, imagem.cz0 * 16], [(imagem.cx0 + imagem.cv.width) * 16, (imagem.cz0 + imagem.cv.height) * 16]);
    }
    if (!pts.length) return { x: 0, z: 0, esc: 0.15 };
    const xs = pts.map((p) => p[0]), zs = pts.map((p) => p[1]);
    const x0 = Math.min(...xs), x1 = Math.max(...xs), z0 = Math.min(...zs), z1 = Math.max(...zs);
    const esc = Math.min(tam.w / Math.max(200, (x1 - x0) * 1.15), tam.h / Math.max(200, (z1 - z0) * 1.15));
    return { x: (x0 + x1) / 2, z: (z0 + z1) / 2, esc };
  }, [reinos, jogadores, spawn, masmorras, imagem, tam]);

  const padrao = useMemo(() => calcular(false), [calcular]);
  const vista: Vista = vistaUser ?? padrao;
  const setVista = useCallback((f: Vista | ((v: Vista) => Vista)) => {
    setVistaUser((v) => (typeof f === "function" ? f(v ?? padrao) : f));
  }, [padrao]);
  const enquadrar = (tudo: boolean) => setVistaUser(calcular(tudo));
  // novo foco vindo de fora: ajusta a vista durante o render (padrão do React para "estado derivado de prop")
  const [focoVisto, setFocoVisto] = useState(0);
  if (foco && foco.n !== focoVisto) {
    setFocoVisto(foco.n);
    setVistaUser({ x: foco.x, z: foco.z, esc: foco.esc });
  }

  // desenho
  useEffect(() => {
    const cv = ref.current;
    if (!cv) return;
    const dpr = window.devicePixelRatio || 1;
    cv.width = tam.w * dpr; cv.height = tam.h * dpr;
    const ctx = cv.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const { x: vx, z: vz, esc } = vista;
    const tx = (x: number) => (x - vx) * esc + tam.w / 2;
    const tz = (z: number) => (z - vz) * esc + tam.h / 2;

    // fundo: "mar do desconhecido"
    const g = ctx.createRadialGradient(tam.w / 2, tam.h / 2, 50, tam.w / 2, tam.h / 2, Math.max(tam.w, tam.h));
    g.addColorStop(0, "#14202a"); g.addColorStop(1, "#0b0f14");
    ctx.fillStyle = g; ctx.fillRect(0, 0, tam.w, tam.h);

    // terreno já gerado no servidor (por qualquer jogador), mesmo sem imagem do mapa
    if (camadas.explorado && imagem) {
      ctx.imageSmoothingEnabled = false;
      ctx.globalAlpha = camadas.jogo && mapa?.jm ? 0.45 : 0.9;
      ctx.drawImage(imagem.cv, tx(imagem.cx0 * 16), tz(imagem.cz0 * 16), imagem.cv.width * 16 * esc, imagem.cv.height * 16 * esc);
      ctx.globalAlpha = 1;
    }

    // mapa do jogo (imagens do JourneyMap: 512 x 512 blocos cada)
    const jm = mapa?.jm;
    if (camadas.jogo && jm) {
      const lista = jm.tiles[camadaJm] ?? jm.tiles.day ?? [];
      const c = jm.tiles[camadaJm] ? camadaJm : "day";
      ctx.imageSmoothingEnabled = esc < 1;
      for (const [rx, rz] of lista) {
        const x = tx(rx * 512), z = tz(rz * 512), lado = 512 * esc;
        if (x > tam.w || z > tam.h || x + lado < 0 || z + lado < 0) continue;
        const chave = `${c}:${rx},${rz}:${jm.atualizadoEm}`;
        let im = tiles.current.get(chave);
        if (!im) {
          im = new Image();
          im.onload = () => setCarregadas((n) => n + 1);
          im.src = `/api/painel/mapa-tile?c=${encodeURIComponent(c)}&rx=${rx}&rz=${rz}&t=${jm.atualizadoEm}`;
          tiles.current.set(chave, im);
        }
        if (im.complete && im.naturalWidth) ctx.drawImage(im, x, z, lado, lado);
      }
    }

    // grade
    if (camadas.grade) {
      const passos = [100, 250, 500, 1000, 2500, 5000, 10000];
      const passo = passos.find((p) => p * esc >= 70) ?? 10000;
      ctx.strokeStyle = "rgba(233,223,200,0.07)"; ctx.lineWidth = 1;
      ctx.fillStyle = "rgba(233,223,200,0.35)"; ctx.font = "10px ui-monospace, monospace";
      const xi = Math.floor((vx - tam.w / 2 / esc) / passo) * passo, xf = vx + tam.w / 2 / esc;
      for (let x = xi; x <= xf; x += passo) { const s = tx(x); ctx.beginPath(); ctx.moveTo(s, 0); ctx.lineTo(s, tam.h); ctx.stroke(); ctx.fillText(String(x), s + 3, 11); }
      const zi = Math.floor((vz - tam.h / 2 / esc) / passo) * passo, zf = vz + tam.h / 2 / esc;
      for (let z = zi; z <= zf; z += passo) { const s = tz(z); ctx.beginPath(); ctx.moveTo(0, s); ctx.lineTo(tam.w, s); ctx.stroke(); ctx.fillText(String(z), 3, s - 3); }
    }

    // reinos
    if (camadas.reinos) for (const r of reinos) {
      const x = tx(r.x0), z = tz(r.z0), w = (r.x1 - r.x0) * esc, h = (r.z1 - r.z0) * esc;
      ctx.fillStyle = r.subterraneo ? "rgba(155,120,60,0.22)" : "rgba(212,169,58,0.20)";
      ctx.fillRect(x, z, w, h);
      ctx.strokeStyle = "#d4a93a"; ctx.lineWidth = 2;
      ctx.setLineDash(r.subterraneo ? [6, 4] : []);
      ctx.strokeRect(x, z, w, h);
      ctx.setLineDash([]);
      const fonte = Math.max(10, Math.min(15, w / 8));
      ctx.font = `bold ${fonte}px Georgia, serif`; ctx.textAlign = "center";
      ctx.lineWidth = 3; ctx.strokeStyle = "rgba(0,0,0,0.8)";
      const rot = `👑 ${r.nome}${r.subterraneo ? " (subterrâneo)" : ""}`;
      ctx.strokeText(rot, x + w / 2, z - 6); ctx.fillStyle = "#f3d27a"; ctx.fillText(rot, x + w / 2, z - 6);
      ctx.textAlign = "start";
    }

    // masmorras
    if (camadas.masmorras) for (const m of masmorras) {
      const x = tx(m.x), z = tz(m.z);
      ctx.save(); ctx.translate(x, z); ctx.rotate(Math.PI / 4);
      ctx.fillStyle = m.colocada ? "#b066ff" : "rgba(176,102,255,0.35)"; ctx.strokeStyle = "#1a0a26"; ctx.lineWidth = 2;
      ctx.fillRect(-6, -6, 12, 12); ctx.strokeRect(-6, -6, 12, 12);
      ctx.restore();
      ctx.font = "11px ui-sans-serif, system-ui"; ctx.lineWidth = 3; ctx.strokeStyle = "rgba(0,0,0,0.85)";
      ctx.strokeText(m.nome, x + 10, z + 4); ctx.fillStyle = "#d9b8ff"; ctx.fillText(m.nome, x + 10, z + 4);
    }

    // spawn
    if (spawn) {
      const x = tx(spawn[0]), z = tz(spawn[1]);
      ctx.font = "18px serif"; ctx.textAlign = "center"; ctx.fillText("⭐", x, z + 6); ctx.textAlign = "start";
      ctx.font = "11px ui-sans-serif"; ctx.lineWidth = 3; ctx.strokeStyle = "#000"; ctx.strokeText("Spawn", x + 12, z + 4); ctx.fillStyle = "#fff6c2"; ctx.fillText("Spawn", x + 12, z + 4);
    }

    // chefe mundial
    if (chefe) {
      const x = tx(chefe.x), z = tz(chefe.z);
      const r = 14 + (pulso % 10) * 2;
      ctx.beginPath(); ctx.arc(x, z, r, 0, Math.PI * 2); ctx.strokeStyle = `rgba(255,70,70,${1 - (pulso % 10) / 10})`; ctx.lineWidth = 2; ctx.stroke();
      ctx.font = "20px serif"; ctx.textAlign = "center"; ctx.fillText("☠️", x, z + 7); ctx.textAlign = "start";
      ctx.font = "bold 12px ui-sans-serif"; ctx.lineWidth = 3; ctx.strokeStyle = "#000"; ctx.strokeText(chefe.nome, x + 14, z - 8); ctx.fillStyle = "#ff8080"; ctx.fillText(chefe.nome, x + 14, z - 8);
    }

    // jogadores
    if (camadas.jogadores) jogadores.forEach((j, i) => {
      const x = tx(j.x), z = tz(j.z), cor = CORES_JOG[i % CORES_JOG.length];
      const r = 6 + Math.sin(pulso / 3) * 1.5;
      ctx.beginPath(); ctx.arc(x, z, r + 5, 0, Math.PI * 2); ctx.fillStyle = cor + "33"; ctx.fill();
      ctx.beginPath(); ctx.arc(x, z, r, 0, Math.PI * 2); ctx.fillStyle = cor; ctx.fill(); ctx.strokeStyle = "#000"; ctx.lineWidth = 2; ctx.stroke();
      ctx.font = "bold 12px ui-sans-serif"; ctx.lineWidth = 3; ctx.strokeStyle = "#000"; ctx.strokeText(j.nick, x + 11, z + 4); ctx.fillStyle = cor; ctx.fillText(j.nick, x + 11, z + 4);
    });

    // escala
    const passoEsc = [50, 100, 250, 500, 1000, 2500, 5000].find((p) => p * esc >= 60) ?? 5000;
    ctx.fillStyle = "rgba(0,0,0,0.55)"; ctx.fillRect(tam.w - passoEsc * esc - 26, tam.h - 28, passoEsc * esc + 18, 20);
    ctx.fillStyle = "#e9dfc8"; ctx.fillRect(tam.w - passoEsc * esc - 18, tam.h - 16, passoEsc * esc, 3);
    ctx.font = "10px ui-monospace"; ctx.fillText(`${passoEsc} blocos`, tam.w - passoEsc * esc - 18, tam.h - 19);
  });

  // interação
  const doMouse = (e: React.MouseEvent) => {
    const rect = (e.target as HTMLCanvasElement).getBoundingClientRect();
    return { mx: e.clientX - rect.left, my: e.clientY - rect.top };
  };

  useEffect(() => {
    const cv = ref.current;
    if (!cv) return;
    const roda = (e: WheelEvent) => {
      e.preventDefault();
      setVista((v) => {
        const rect = cv.getBoundingClientRect();
        const mx = e.clientX - rect.left, my = e.clientY - rect.top;
        const wx = (mx - tam.w / 2) / v.esc + v.x, wz = (my - tam.h / 2) / v.esc + v.z;
        const esc = Math.max(0.002, Math.min(8, v.esc * (e.deltaY < 0 ? 1.25 : 0.8)));
        return { esc, x: wx - (mx - tam.w / 2) / esc, z: wz - (my - tam.h / 2) / esc };
      });
    };
    cv.addEventListener("wheel", roda, { passive: false });
    return () => cv.removeEventListener("wheel", roda);
  }, [tam, setVista]);

  const zoom = (f: number) => setVista((v) => ({ ...v, esc: Math.max(0.002, Math.min(8, v.esc * f)) }));
  const seguir = (j: Jog) => setVista((v) => ({ x: j.x, z: j.z, esc: Math.max(v.esc, 1) }));

  const botao = "rounded-md border border-pedra-700 bg-pedra-950/80 px-2 py-1 text-xs hover:border-ouro";

  return (
    <div ref={caixa} className="relative w-full min-w-0">
      <canvas
        ref={ref}
        style={{ width: "100%", height: tam.h }}
        className="touch-none cursor-grab rounded-lg border border-pedra-700 active:cursor-grabbing"
        onPointerDown={(e) => { arrasto.current = { x: e.clientX, y: e.clientY, vx: vista.x, vz: vista.z }; }}
        onPointerUp={() => { arrasto.current = null; }}
        onPointerLeave={() => { arrasto.current = null; setMouse(null); }}
        onDoubleClick={(e) => {
          const { mx, my } = doMouse(e);
          setVista((v) => ({ esc: Math.min(8, v.esc * 2), x: (mx - tam.w / 2) / v.esc + v.x, z: (my - tam.h / 2) / v.esc + v.z }));
        }}
        onPointerMove={(e) => {
          const { mx, my } = doMouse(e);
          setMouse({ x: Math.round((mx - tam.w / 2) / vista.esc + vista.x), z: Math.round((my - tam.h / 2) / vista.esc + vista.z) });
          const a = arrasto.current;
          if (a) setVista({ ...vista, x: a.vx - (e.clientX - a.x) / vista.esc, z: a.vz - (e.clientY - a.y) / vista.esc });
        }}
      />
      {!mapa && <div className="absolute inset-0 grid place-items-center text-sm text-pedra-500">Lendo as regiões do mundo…</div>}

      <div className="absolute left-2 top-2 flex flex-col gap-1">
        <button className={botao} onClick={() => zoom(1.5)} title="Aproximar">＋</button>
        <button className={botao} onClick={() => zoom(1 / 1.5)} title="Afastar">－</button>
        <button className={botao} onClick={() => enquadrar(false)} title="Ver reinos e jogadores">🏰</button>
        <button className={botao} onClick={() => enquadrar(true)} title="Ver o mundo todo">🌍</button>
      </div>

      <div className="absolute right-2 top-2 flex max-w-[60%] flex-wrap justify-end gap-1">
        {mapa?.jm && camadas.jogo && mapa.jm.camadas.length > 1 && (
          <select value={camadaJm} onChange={(e) => setCamadaJm(e.target.value)} className={botao}>
            {mapa.jm.camadas.map((c) => <option key={c} value={c}>{c === "day" ? "☀️ Superfície" : c === "night" ? "🌙 Noite" : c === "topo" ? "⛰️ Relevo" : `🕳️ Caverna (Y ${Number(c) * 16} a ${Number(c) * 16 + 15})`}</option>)}
          </select>
        )}
        {([["jogo", "🗺️ Mapa do jogo"], ["explorado", "▦ Terreno gerado"], ["reinos", "👑 Reinos"], ["masmorras", "🔮 Masmorras"], ["jogadores", "🧭 Jogadores"], ["grade", "＃ Grade"]] as const).map(([k, n]) => (
          <button key={k} className={`${botao} ${camadas[k] ? "text-pergaminho" : "text-pedra-500 line-through"}`} onClick={() => setCamadas((c) => ({ ...c, [k]: !c[k] }))}>{n}</button>
        ))}
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-pedra-500">
        <span className="font-mono">{mouse ? `X ${mouse.x} · Z ${mouse.z}` : "passe o mouse no mapa"}</span>
        {jogadores.length > 0 && <span className="ml-auto">Ir até:</span>}
        {jogadores.map((j, i) => (
          <button key={j.uuid} className={botao} style={{ color: CORES_JOG[i % CORES_JOG.length] }} onClick={() => seguir(j)}>● {j.nick}</button>
        ))}
        {reinos.map((r) => (
          <button key={r.id} className={botao} onClick={() => setVista((v) => ({ x: (r.x0 + r.x1) / 2, z: (r.z0 + r.z1) / 2, esc: Math.max(v.esc, Math.min(tam.w / (r.x1 - r.x0) / 1.4, 2)) }))}>👑 {r.nome.split(" ").slice(-1)[0]}</button>
        ))}
      </div>
    </div>
  );
}
