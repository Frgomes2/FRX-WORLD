"use client";

import { useEffect, useRef, useState } from "react";

function texturaArmadura(material: string, camada: 1 | 2) {
  const [ns, nome] = material.includes(":") ? material.split(":") : ["minecraft", material];
  return `/api/painel/textura?p=${encodeURIComponent(`${ns}/textures/models/armor/${nome}_layer_${camada}.png`)}`;
}

type Caixa3D = { img: HTMLImageElement; texW: number; texH: number; uv: [number, number]; tam: [number, number, number]; geo?: [number, number, number]; pos: [number, number, number]; base?: boolean };

function carregar(url: string, cache: Map<string, HTMLImageElement>, aoCarregar: () => void) {
  let im = cache.get(url);
  if (!im) {
    im = new Image();
    im.onload = aoCarregar;
    im.src = url;
    cache.set(url, im);
  }
  return im.complete && im.naturalWidth ? im : null;
}

/**
 * Desenha as caixas num <canvas> 2D (projeção ortográfica): cada face vira um paralelogramo com a textura
 * mapeada por transformação afim. Funciona em qualquer navegador, sem depender de CSS 3D.
 */
function desenhar(ctx: CanvasRenderingContext2D, caixas: Caixa3D[], yaw: number, pitch: number, w: number, h: number) {
  const cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
  const S = Math.min(w / 22, h / 38);
  const ox = w / 2, oy = h / 2 + 2 * S;
  const rot = (x: number, y: number, z: number) => {
    const x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
    const y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;
    return { x: ox + x1 * S, y: oy - y2 * S, z: z2 };
  };
  type Face = { img: HTMLImageElement; u: number; v: number; uw: number; vh: number; tl: { x: number; y: number }; tr: { x: number; y: number }; bl: { x: number; y: number }; z: number; base: boolean };
  const faces: Face[] = [];
  for (const c of caixas) {
    const [tw, th, td] = c.tam;
    const [gw, gh, gd] = c.geo ?? c.tam;
    const [u, v] = c.uv;
    const [px, py, pz] = c.pos;
    const X = gw / 2, Y = gh / 2, Z = gd / 2;
    const P = (x: number, y: number, z: number) => rot(px + x, -py + y, pz + z);
    const defs: [number, number, number, number, [number, number, number], [number, number, number], [number, number, number]][] = [
      [u + td, v + td, tw, th, [-X, Y, Z], [X, Y, Z], [-X, -Y, Z]], // frente
      [u + 2 * td + tw, v + td, tw, th, [X, Y, -Z], [-X, Y, -Z], [X, -Y, -Z]], // costas
      [u, v + td, td, th, [-X, Y, -Z], [-X, Y, Z], [-X, -Y, -Z]], // lado direito do jogador
      [u + td + tw, v + td, td, th, [X, Y, Z], [X, Y, -Z], [X, -Y, Z]], // lado esquerdo
      [u + td, v, tw, td, [-X, Y, -Z], [X, Y, -Z], [-X, Y, Z]], // topo
      [u + td + tw, v, tw, td, [-X, -Y, Z], [X, -Y, Z], [-X, -Y, -Z]], // baixo
    ];
    for (const [fu, fv, fw, fh, a, b, d] of defs) {
      const tl = P(...a), tr = P(...b), bl = P(...d);
      const cruz = (tr.x - tl.x) * (bl.y - tl.y) - (tr.y - tl.y) * (bl.x - tl.x);
      if (cruz <= 0) continue; // face de costas para a câmera
      faces.push({ img: c.img, u: fu * (c.img.naturalWidth / c.texW), v: fv * (c.img.naturalHeight / c.texH),
        uw: fw * (c.img.naturalWidth / c.texW), vh: fh * (c.img.naturalHeight / c.texH), tl, tr, bl, z: (tl.z + tr.z + bl.z + (tr.z + bl.z - tl.z)) / 4, base: !!c.base });
    }
  }
  faces.sort((a, b) => a.z - b.z);
  ctx.imageSmoothingEnabled = false;
  for (const f of faces) {
    ctx.save();
    ctx.transform((f.tr.x - f.tl.x) / f.uw, (f.tr.y - f.tl.y) / f.uw, (f.bl.x - f.tl.x) / f.vh, (f.bl.y - f.tl.y) / f.vh, f.tl.x, f.tl.y);
    // camada de baixo da skin: no jogo, pixel transparente aparece preto
    if (f.base) { ctx.fillStyle = "#000"; ctx.fillRect(-0.02, -0.02, f.uw + 0.04, f.vh + 0.04); }
    // 0.04 a mais esconde as frestas entre faces vizinhas
    ctx.drawImage(f.img, f.u, f.v, f.uw, f.vh, -0.02, -0.02, f.uw + 0.04, f.vh + 0.04);
    ctx.restore();
  }
}

export type SkinDesenho = { url: string; slim: boolean; altura: number };
export type Peca = { material: string; parte: string };

/** Bonequinho 3D desenhado em <canvas>: skin + armadura. Arraste para girar. */
export function BonecoCanvas({ sk, armadura = [], largura = 220, alturaPx = 300, girar = true, anguloInicial = -25, children }: {
  sk: SkinDesenho | null; armadura?: Peca[]; largura?: number; alturaPx?: number; girar?: boolean; anguloInicial?: number; children?: React.ReactNode;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [ang, setAng] = useState(anguloInicial);
  const [incl, setIncl] = useState(-10);
  const [, setCarregou] = useState(0);
  const cache = useRef(new Map<string, HTMLImageElement>());
  const arrasto = useRef<{ x: number; y: number; a: number; i: number } | null>(null);
  const ocioso = useRef(true);

  useEffect(() => {
    if (!girar) return;
    const t = setInterval(() => { if (ocioso.current) setAng((a) => a + 0.8); }, 40);
    return () => clearInterval(t);
  }, [girar]);

  useEffect(() => {
    const cv = ref.current;
    if (!cv) return;
    const W = largura, H = alturaPx, dpr = window.devicePixelRatio || 1;
    cv.width = W * dpr; cv.height = H * dpr;
    const ctx = cv.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);
    // sombra
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    ctx.beginPath(); ctx.ellipse(W / 2, H - H * 0.073, W * 0.236, H * 0.03, 0, 0, Math.PI * 2); ctx.fill();
    if (!sk) return;
    const pronto = () => setCarregou((n) => n + 1);
    if (cache.current.size > 40) cache.current.clear(); // o editor gera uma imagem nova a cada traço
    const pele = carregar(sk.url, cache.current, pronto);
    if (!pele) return;
    const T = sk.altura, a = sk.slim ? 3 : 4, ax = sk.slim ? 5.5 : 6, antigo = T < 64;
    const caixas: Caixa3D[] = [];
    const add = (img: HTMLImageElement, tw: number, th: number, uv: [number, number], tam: [number, number, number], pos: [number, number, number], geo?: [number, number, number], base = false) =>
      caixas.push({ img, texW: tw, texH: th, uv, tam, pos, geo, base });
    add(pele, 64, T, [0, 0], [8, 8, 8], [0, -12, 0], undefined, true);
    add(pele, 64, T, [16, 16], [8, 12, 4], [0, -2, 0], undefined, true);
    add(pele, 64, T, [40, 16], [a, 12, 4], [-ax, -2, 0], undefined, true);
    add(pele, 64, T, antigo ? [40, 16] : [32, 48], [a, 12, 4], [ax, -2, 0], undefined, true);
    add(pele, 64, T, [0, 16], [4, 12, 4], [-2, 10, 0], undefined, true);
    add(pele, 64, T, antigo ? [0, 16] : [16, 48], [4, 12, 4], [2, 10, 0], undefined, true);
    add(pele, 64, T, [32, 0], [8, 8, 8], [0, -12, 0], [9, 9, 9]);
    if (!antigo) {
      add(pele, 64, T, [16, 32], [8, 12, 4], [0, -2, 0], [8.5, 12.5, 4.5]);
      add(pele, 64, T, [40, 32], [a, 12, 4], [-ax, -2, 0], [a + 0.5, 12.5, 4.5]);
      add(pele, 64, T, [48, 48], [a, 12, 4], [ax, -2, 0], [a + 0.5, 12.5, 4.5]);
      add(pele, 64, T, [0, 32], [4, 12, 4], [-2, 10, 0], [4.5, 12.5, 4.5]);
      add(pele, 64, T, [0, 48], [4, 12, 4], [2, 10, 0], [4.5, 12.5, 4.5]);
    }
    const peca = (parte: string) => armadura.find((p) => p.parte === parte)?.material;
    const arm = (mat: string | undefined, camada: 1 | 2) => (mat ? carregar(texturaArmadura(mat, camada), cache.current, pronto) : null);
    const cab = arm(peca("head"), 1), peito = arm(peca("chest"), 1), pernas = arm(peca("legs"), 2), pes = arm(peca("feet"), 1);
    if (cab) add(cab, 64, 32, [0, 0], [8, 8, 8], [0, -12, 0], [10, 10, 10]);
    if (peito) {
      add(peito, 64, 32, [16, 16], [8, 12, 4], [0, -2, 0], [10, 14, 6]);
      add(peito, 64, 32, [40, 16], [4, 12, 4], [-6, -2, 0], [6, 14, 6]);
      add(peito, 64, 32, [40, 16], [4, 12, 4], [6, -2, 0], [6, 14, 6]);
    }
    if (pernas) {
      add(pernas, 64, 32, [16, 16], [8, 12, 4], [0, -2, 0], [9, 13, 5]);
      add(pernas, 64, 32, [0, 16], [4, 12, 4], [-2, 10, 0], [5, 13, 5]);
      add(pernas, 64, 32, [0, 16], [4, 12, 4], [2, 10, 0], [5, 13, 5]);
    }
    if (pes) {
      add(pes, 64, 32, [0, 16], [4, 12, 4], [-2, 10, 0], [6, 14, 6]);
      add(pes, 64, 32, [0, 16], [4, 12, 4], [2, 10, 0], [6, 14, 6]);
    }
    desenhar(ctx, caixas, (ang * Math.PI) / 180, (incl * Math.PI) / 180, W, H);
  });

  return (
    <div className="relative">
    <canvas
      ref={ref}
      style={{ width: largura, height: alturaPx }}
      className="block cursor-grab select-none active:cursor-grabbing"
      title="Arraste para girar"
      onMouseDown={(e) => { ocioso.current = false; arrasto.current = { x: e.clientX, y: e.clientY, a: ang, i: incl }; }}
      onMouseMove={(e) => {
        const a = arrasto.current;
        if (!a) return;
        setAng(a.a + (e.clientX - a.x) * 0.8);
        setIncl(Math.max(-45, Math.min(45, a.i - (e.clientY - a.y) * 0.4)));
      }}
      onMouseUp={() => { arrasto.current = null; }}
      onMouseLeave={() => { arrasto.current = null; ocioso.current = true; }}
    />
    {children}
    </div>
  );
}
