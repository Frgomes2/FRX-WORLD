"use client";

import { useEffect, useRef, useState } from "react";
import type { MapaPublico as Dados } from "@/lib/publico";

type Vista = { x: number; z: number; esc: number };

/** Mapa do mundo já explorado: arrastar move, roda do mouse (ou os botões) aproxima. Sem posição de jogadores. */
/** foco = onde o mapa abre (padrão: ponto de partida); marcas = pontos extras, como os terrenos de quem está logado. */
export function MapaPublico({ mapa, foco, marcas = [] }: { mapa: Dados; foco?: [number, number]; marcas?: { x: number; z: number; nome: string }[] }) {
  const caixa = useRef<HTMLDivElement>(null);
  const ref = useRef<HTMLCanvasElement>(null);
  const imgs = useRef(new Map<string, HTMLImageElement>());
  const arrasto = useRef<{ px: number; py: number; vx: number; vz: number } | null>(null);
  const [tam, setTam] = useState({ w: 800, h: 480 });
  const [, setCarregadas] = useState(0);
  const centro = foco ?? mapa.spawn ?? mapa.torre ?? [0, 0];
  const escInicial = foco ? 0.6 : 0.2;
  const [vista, setVista] = useState<Vista>({ x: centro[0], z: centro[1], esc: escInicial });
  const [mouse, setMouse] = useState<{ x: number; z: number } | null>(null);

  useEffect(() => {
    const el = caixa.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setTam({ w: el.clientWidth, h: Math.max(280, Math.min(600, Math.round(el.clientWidth * 0.6))) }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const cv = ref.current;
    if (!cv) return;
    const dpr = window.devicePixelRatio || 1;
    cv.width = tam.w * dpr; cv.height = tam.h * dpr;
    const ctx = cv.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.imageSmoothingEnabled = vista.esc < 1;
    ctx.fillStyle = "#12100d"; ctx.fillRect(0, 0, tam.w, tam.h);
    const tx = (x: number) => (x - vista.x) * vista.esc + tam.w / 2;
    const tz = (z: number) => (z - vista.z) * vista.esc + tam.h / 2;
    const lado = 512 * vista.esc;
    for (const [rx, rz] of mapa.tiles) {
      const x = tx(rx * 512), z = tz(rz * 512);
      if (x > tam.w || z > tam.h || x + lado < 0 || z + lado < 0) continue;
      const k = `${rx},${rz}`;
      let im = imgs.current.get(k);
      if (!im) {
        im = new Image();
        im.onload = () => setCarregadas((n) => n + 1);
        im.src = `/api/publico/mapa-tile?rx=${rx}&rz=${rz}&t=${Math.floor(mapa.atualizadoEm / 600_000)}`;
        imgs.current.set(k, im);
      }
      if (im.complete && im.naturalWidth) ctx.drawImage(im, x, z, lado + 0.5, lado + 0.5);
    }
    const marca = (x: number, z: number, icone: string, nome: string, cor: string) => {
      const px = tx(x), pz = tz(z);
      if (px < -60 || pz < -30 || px > tam.w + 60 || pz > tam.h + 30) return;
      ctx.beginPath(); ctx.arc(px, pz, 11, 0, Math.PI * 2); ctx.fillStyle = "rgba(15,13,11,.85)"; ctx.fill();
      ctx.lineWidth = 2; ctx.strokeStyle = cor; ctx.stroke();
      ctx.font = "13px ui-sans-serif"; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillStyle = "#fff"; ctx.fillText(icone, px, pz + 1);
      ctx.font = "bold 12px ui-sans-serif"; ctx.textAlign = "left"; ctx.lineWidth = 3; ctx.strokeStyle = "#000";
      ctx.strokeText(nome, px + 15, pz); ctx.fillStyle = cor; ctx.fillText(nome, px + 15, pz);
    };
    for (const r of mapa.reinos) marca(r.x, r.z, "👑", r.nome, "#d4a93a");
    if (mapa.torre) marca(mapa.torre[0], mapa.torre[1], "🗼", "Torre Infinita", "#c9a2ff");
    if (mapa.spawn) marca(mapa.spawn[0], mapa.spawn[1], "✦", "Ponto de partida", "#8fd18f");
    for (const m of marcas) marca(m.x, m.z, "🏠", m.nome, "#7fd4ff");
  });

  const zoom = (f: number) => setVista((v) => ({ ...v, esc: Math.max(0.03, Math.min(4, v.esc * f)) }));
  const botao = "h-9 w-9 rounded-md border border-pedra-700 bg-pedra-900/90 text-lg hover:border-ouro";

  if (!mapa.tiles.length) return <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-6 text-pedra-500">O mapa ainda não foi desenhado. Volte depois que os aventureiros explorarem mais.</p>;

  return (
    <div ref={caixa} className="relative overflow-hidden rounded-xl border border-pedra-700 bg-pedra-900">
      <canvas ref={ref} style={{ width: tam.w, height: tam.h, touchAction: "none", cursor: arrasto.current ? "grabbing" : "grab" }}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); arrasto.current = { px: e.clientX, py: e.clientY, vx: vista.x, vz: vista.z }; }}
        onPointerUp={() => { arrasto.current = null; }}
        onPointerLeave={() => setMouse(null)}
        onPointerMove={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          setMouse({ x: Math.round((e.clientX - r.left - tam.w / 2) / vista.esc + vista.x), z: Math.round((e.clientY - r.top - tam.h / 2) / vista.esc + vista.z) });
          const a = arrasto.current;
          if (a) setVista((v) => ({ ...v, x: a.vx - (e.clientX - a.px) / v.esc, z: a.vz - (e.clientY - a.py) / v.esc }));
        }}
        onWheel={(e) => zoom(e.deltaY < 0 ? 1.2 : 1 / 1.2)} />
      <div className="absolute right-3 top-3 flex flex-col gap-1.5">
        <button type="button" className={botao} onClick={() => zoom(1.5)} aria-label="Aproximar">+</button>
        <button type="button" className={botao} onClick={() => zoom(1 / 1.5)} aria-label="Afastar">−</button>
        <button type="button" className={botao} onClick={() => setVista({ x: centro[0], z: centro[1], esc: escInicial })} aria-label="Voltar ao ponto de partida">⌂</button>
      </div>
      <p className="absolute bottom-2 left-3 rounded bg-pedra-950/80 px-2 py-0.5 font-mono text-xs text-pedra-500">
        {mouse ? `x ${mouse.x} · z ${mouse.z}` : "arraste para mover · role para aproximar"}
      </p>
    </div>
  );
}
