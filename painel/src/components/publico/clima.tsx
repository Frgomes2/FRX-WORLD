"use client";

import { useEffect, useRef } from "react";

/**
 * O céu atrás do topo do site, desenhado num canvas conforme o estado real do mundo:
 * - chuva / tempestade: gotas em três distâncias inclinadas pelo vento; na tempestade, raios com galhos e clarão;
 * - noite: estrelas que piscam, lua com halo, vaga-lumes e, de vez em quando, uma estrela cadente;
 * - dia: sol com raios que giram devagar, poeira dourada no ar e pássaros cruzando o céu;
 * - chefe: brasas e cinzas subindo;
 * - desligado: poeira lenta, quase parada.
 * Para quem pede menos movimento no sistema, desenha um quadro parado.
 */
export type Cena = "dia" | "noite" | "chuva" | "tempestade" | "chefe" | "desligado";
type P = { x: number; y: number; z: number; vx: number; vy: number; r: number; fase: number };
type Raio = { linhas: [number, number][][]; nasce: number; dura: number };
type Risco = { x: number; y: number; vx: number; vy: number; nasce: number; dura: number };
type Passaro = { x: number; y: number; vel: number; tam: number; fase: number };

const sorte = (a: number, b: number) => a + Math.random() * (b - a);

export function Clima({ tipo }: { tipo: Cena }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    const chove = tipo === "chuva" || tipo === "tempestade", forte = tipo === "tempestade";
    const vento = forte ? -0.42 : -0.2;                 // quanto a gota anda para o lado a cada pixel que desce
    const parado = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let larg = 0, alt = 0, quadro = 0, antes = 0;
    let ps: P[] = [], extras: P[] = [], passaros: Passaro[] = [];
    let raio: Raio | null = null, risco: Risco | null = null, proximo = 0;

    const gota = (emCima: boolean): P => {
      const z = sorte(0.35, 1);                         // 0.35 = longe, 1 = perto
      const vel = sorte(520, 900) * z * (forte ? 1.35 : 1);
      return { x: Math.random() * (larg + alt * 0.5), y: emCima ? -30 : Math.random() * alt, z, vx: vel * vento, vy: vel, r: sorte(10, 24) * z * (forte ? 1.25 : 1), fase: 0 };
    };
    const brasa = (embaixo: boolean): P => ({ x: Math.random() * larg, y: embaixo ? alt + 10 : Math.random() * alt, z: sorte(0.3, 1), vx: sorte(-14, 14), vy: -sorte(18, 70), r: sorte(0.8, 2.4), fase: Math.random() * 6.28 });

    const montar = () => {
      const r = canvas.getBoundingClientRect(), dpr = Math.min(2, window.devicePixelRatio || 1);
      larg = r.width; alt = r.height;
      canvas.width = Math.round(larg * dpr); canvas.height = Math.round(alt * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      extras = []; passaros = [];
      if (chove) ps = Array.from({ length: Math.min(520, Math.round(larg / (forte ? 3.2 : 5))) }, () => gota(false));
      else if (tipo === "noite") {
        ps = Array.from({ length: Math.min(260, Math.round(larg / 7)) }, () => ({ x: Math.random() * larg, y: Math.random() * alt * 0.92, z: Math.random(), vx: 0, vy: 0, r: sorte(0.4, 1.5), fase: Math.random() * 6.28 }));
        extras = Array.from({ length: 14 }, () => ({ x: Math.random() * larg, y: sorte(alt * 0.55, alt), z: Math.random(), vx: sorte(-10, 10), vy: sorte(-6, 6), r: sorte(1, 1.8), fase: Math.random() * 6.28 }));
      } else if (tipo === "dia") {
        ps = Array.from({ length: Math.min(70, Math.round(larg / 24)) }, () => ({ x: Math.random() * larg, y: Math.random() * alt, z: Math.random(), vx: sorte(4, 16), vy: -sorte(3, 12), r: sorte(0.7, 2), fase: Math.random() * 6.28 }));
        passaros = Array.from({ length: 5 }, (_, i) => ({ x: -sorte(40, larg), y: sorte(alt * 0.1, alt * 0.4), vel: sorte(38, 62), tam: sorte(5, 9), fase: i * 1.3 }));
      } else if (tipo === "chefe") ps = Array.from({ length: Math.min(160, Math.round(larg / 10)) }, () => brasa(false));
      else ps = Array.from({ length: 36 }, () => ({ x: Math.random() * larg, y: Math.random() * alt, z: Math.random(), vx: sorte(-5, 5), vy: sorte(-4, 4), r: sorte(0.6, 1.6), fase: Math.random() * 6.28 }));
    };

    /** Um raio: desce em ziguezague a partir do alto e solta um ou dois galhos. */
    const criarRaio = (agora: number): Raio => {
      const tronco: [number, number][] = [];
      let x = larg * sorte(0.12, 0.88), y = -10;
      const fim = alt * sorte(0.55, 0.9);
      tronco.push([x, y]);
      while (y < fim) { y += sorte(14, 40); x += sorte(-23, 23); tronco.push([x, y]); }
      const linhas = [tronco];
      for (let g = 0; g < 1 + Math.floor(Math.random() * 2); g++) {
        const de = tronco[2 + Math.floor(Math.random() * Math.max(1, tronco.length - 4))];
        if (!de) continue;
        const galho: [number, number][] = [[de[0], de[1]]];
        let gx = de[0], gy = de[1];
        const lado = Math.random() < 0.5 ? -1 : 1;
        for (let i = 0; i < 3 + Math.floor(Math.random() * 4); i++) { gy += sorte(10, 28); gx += lado * sorte(8, 30); galho.push([gx, gy]); }
        linhas.push(galho);
      }
      return { linhas, nasce: agora, dura: sorte(420, 600) };
    };

    const bola = (x: number, y: number, raioPx: number, cor: string) => { ctx.beginPath(); ctx.arc(x, y, raioPx, 0, 6.2832); ctx.fillStyle = cor; ctx.fill(); };
    const halo = (x: number, y: number, raioPx: number, dentro: string, fora: string) => {
      const g = ctx.createRadialGradient(x, y, 0, x, y, raioPx);
      g.addColorStop(0, dentro); g.addColorStop(1, fora);
      ctx.fillStyle = g; ctx.fillRect(x - raioPx, y - raioPx, raioPx * 2, raioPx * 2);
    };

    const desenhar = (agora: number) => {
      const dt = parado ? 0 : Math.min(0.05, (agora - antes) / 1000 || 0), t = agora / 1000;
      antes = agora;
      ctx.clearRect(0, 0, larg, alt);

      if (chove) {
        let clarao = 0;
        if (forte && !parado) {
          if (!proximo) proximo = agora + sorte(1500, 4500);
          if (!raio && agora >= proximo) raio = criarRaio(agora);
          if (raio) {
            const f = (agora - raio.nasce) / raio.dura;
            if (f >= 1) { raio = null; proximo = agora + sorte(3500, 10500); }
            else {
              clarao = f < 0.18 ? 1 : f < 0.3 ? 0.25 : f < 0.42 ? 0.9 : Math.max(0, 1 - (f - 0.42) / 0.58) * 0.6;   // pisca duas vezes e some
              ctx.save();
              ctx.lineCap = "round"; ctx.lineJoin = "round";
              ctx.shadowColor = "rgba(170, 200, 255, 0.95)"; ctx.shadowBlur = 22;
              raio.linhas.forEach((l, i) => {
                ctx.beginPath();
                l.forEach(([px, py], k) => (k ? ctx.lineTo(px, py) : ctx.moveTo(px, py)));
                ctx.strokeStyle = `rgba(235, 242, 255, ${clarao * (i ? 0.7 : 1)})`;
                ctx.lineWidth = i ? 1.2 : 2.4;
                ctx.stroke();
              });
              ctx.restore();
            }
          }
        }
        if (clarao > 0) {                                // o céu acende de cima para baixo
          const luz = ctx.createLinearGradient(0, 0, 0, alt);
          luz.addColorStop(0, `rgba(190, 210, 255, ${0.3 * clarao})`); luz.addColorStop(1, "rgba(190, 210, 255, 0)");
          ctx.fillStyle = luz; ctx.fillRect(0, 0, larg, alt);
        }
        ctx.lineCap = "round";
        for (const g of ps) {
          g.y += g.vy * dt; g.x += g.vx * dt;
          if (g.y - g.r > alt || g.x < -40) Object.assign(g, gota(true));
          ctx.beginPath(); ctx.moveTo(g.x, g.y); ctx.lineTo(g.x - g.r * vento, g.y - g.r);
          ctx.strokeStyle = `rgba(${clarao > 0.3 ? "225, 235, 255" : "165, 195, 240"}, ${(0.1 + g.z * 0.32) * (1 + clarao * 0.8)})`;
          ctx.lineWidth = 0.6 + g.z * 0.9; ctx.stroke();
        }
      } else if (tipo === "noite") {
        const lx = larg * 0.84, ly = alt * 0.2, lr = Math.max(22, Math.min(40, alt * 0.075));
        halo(lx, ly, lr * 6, "rgba(190, 205, 255, 0.22)", "rgba(190, 205, 255, 0)");
        bola(lx, ly, lr, "#e9edf8");
        bola(lx - lr * 0.3, ly - lr * 0.25, lr * 0.2, "rgba(170, 180, 205, 0.55)");      // crateras
        bola(lx + lr * 0.28, ly + lr * 0.3, lr * 0.14, "rgba(170, 180, 205, 0.5)");
        bola(lx + lr * 0.1, ly - lr * 0.45, lr * 0.1, "rgba(170, 180, 205, 0.45)");
        for (const e of ps) bola(e.x, e.y, e.r, `rgba(${e.z > 0.8 ? "255, 236, 200" : "225, 235, 255"}, ${0.25 + 0.6 * (0.5 + 0.5 * Math.sin(t * (0.6 + e.z * 1.8) + e.fase))})`);
        if (!parado) {                                   // estrela cadente
          if (!proximo) proximo = agora + sorte(2500, 7000);
          if (!risco && agora >= proximo) risco = { x: sorte(larg * 0.1, larg * 0.7), y: sorte(0, alt * 0.3), vx: sorte(380, 560), vy: sorte(140, 240), nasce: agora, dura: sorte(700, 1100) };
          if (risco) {
            const f = (agora - risco.nasce) / risco.dura;
            if (f >= 1) { risco = null; proximo = agora + sorte(6000, 15000); }
            else {
              const px = risco.x + risco.vx * f * (risco.dura / 1000), py = risco.y + risco.vy * f * (risco.dura / 1000), a = Math.sin(f * Math.PI);
              const cauda = ctx.createLinearGradient(px, py, px - risco.vx * 0.22, py - risco.vy * 0.22);
              cauda.addColorStop(0, `rgba(255, 255, 255, ${a})`); cauda.addColorStop(1, "rgba(255, 255, 255, 0)");
              ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(px - risco.vx * 0.22, py - risco.vy * 0.22);
              ctx.strokeStyle = cauda; ctx.lineWidth = 1.8; ctx.lineCap = "round"; ctx.stroke();
            }
          }
        }
        for (const v of extras) {                        // vaga-lumes
          v.x += (v.vx + Math.sin(t * 0.7 + v.fase) * 12) * dt; v.y += (v.vy + Math.cos(t * 0.9 + v.fase) * 8) * dt;
          if (v.x < -10) v.x = larg + 10; if (v.x > larg + 10) v.x = -10;
          if (v.y < alt * 0.5) v.y = alt * 0.5; if (v.y > alt) v.y = alt;
          const a = Math.max(0, Math.sin(t * 1.6 + v.fase));
          halo(v.x, v.y, 9, `rgba(200, 255, 120, ${0.35 * a})`, "rgba(200, 255, 120, 0)");
          bola(v.x, v.y, v.r, `rgba(230, 255, 160, ${a})`);
        }
      } else if (tipo === "dia") {
        const sx = larg * 0.86, sy = alt * 0.06;
        ctx.save();                                      // raios de sol girando devagar
        ctx.translate(sx, sy); ctx.rotate(t * 0.03);
        for (let i = 0; i < 14; i++) {
          ctx.rotate(6.2832 / 14);
          const comp = Math.max(larg, alt) * (0.55 + 0.25 * Math.sin(t * 0.4 + i));
          const g = ctx.createLinearGradient(0, 0, comp, 0);
          g.addColorStop(0, "rgba(255, 226, 140, 0.16)"); g.addColorStop(1, "rgba(255, 226, 140, 0)");
          ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(comp, -comp * 0.045); ctx.lineTo(comp, comp * 0.045); ctx.closePath();
          ctx.fillStyle = g; ctx.fill();
        }
        ctx.restore();
        halo(sx, sy, alt * 0.75, "rgba(255, 222, 130, 0.5)", "rgba(255, 222, 130, 0)");
        bola(sx, sy, Math.max(20, alt * 0.07), "rgba(255, 240, 190, 0.95)");
        for (const m of ps) {                            // poeira dourada no ar
          m.x += (m.vx + Math.sin(t * 0.5 + m.fase) * 6) * dt; m.y += m.vy * dt;
          if (m.y < -6) { m.y = alt + 6; m.x = Math.random() * larg; }
          if (m.x > larg + 6) m.x = -6;
          bola(m.x, m.y, m.r, `rgba(255, 228, 150, ${0.15 + 0.45 * (0.5 + 0.5 * Math.sin(t * 1.3 + m.fase))})`);
        }
        ctx.strokeStyle = "rgba(30, 36, 30, 0.75)"; ctx.lineWidth = 1.4; ctx.lineCap = "round";
        for (const b of passaros) {                      // pássaros: duas asas que batem
          b.x += b.vel * dt;
          if (b.x > larg + 30) { b.x = -sorte(30, 300); b.y = sorte(alt * 0.1, alt * 0.4); }
          const bate = Math.sin(t * 7 + b.fase) * b.tam * 0.55, by = b.y + Math.sin(t * 0.8 + b.fase) * 6;
          ctx.beginPath(); ctx.moveTo(b.x - b.tam, by - bate); ctx.quadraticCurveTo(b.x - b.tam * 0.4, by - b.tam * 0.35, b.x, by);
          ctx.quadraticCurveTo(b.x + b.tam * 0.4, by - b.tam * 0.35, b.x + b.tam, by - bate); ctx.stroke();
        }
      } else if (tipo === "chefe") {
        for (const b of ps) {                            // brasas e cinzas subindo
          b.x += (b.vx + Math.sin(t * 1.4 + b.fase) * 16) * dt; b.y += b.vy * dt;
          if (b.y < -10) Object.assign(b, brasa(true));
          const vida = Math.max(0, Math.min(1, b.y / alt)), a = (0.25 + 0.75 * vida) * (0.6 + 0.4 * Math.sin(t * 9 + b.fase));
          halo(b.x, b.y, b.r * 5, `rgba(255, 110, 40, ${0.3 * a})`, "rgba(255, 110, 40, 0)");
          bola(b.x, b.y, b.r, `rgba(255, ${Math.round(120 + 100 * vida)}, 70, ${a})`);
        }
      } else {
        for (const m of ps) {                            // poeira lenta
          m.x += m.vx * dt; m.y += m.vy * dt;
          if (m.x < -5) m.x = larg + 5; if (m.x > larg + 5) m.x = -5;
          if (m.y < -5) m.y = alt + 5; if (m.y > alt + 5) m.y = -5;
          bola(m.x, m.y, m.r, `rgba(200, 200, 200, ${0.08 + 0.12 * (0.5 + 0.5 * Math.sin(t * 0.6 + m.fase))})`);
        }
      }
      if (!parado) quadro = requestAnimationFrame(desenhar);
    };

    montar();
    const observador = new ResizeObserver(montar);
    observador.observe(canvas);
    quadro = requestAnimationFrame(desenhar);
    return () => { cancelAnimationFrame(quadro); observador.disconnect(); };
  }, [tipo]);

  const nuvens = tipo === "tempestade" ? "frx-nuvens frx-nuvens-forte" : tipo === "chuva" ? "frx-nuvens" : tipo === "dia" ? "frx-nuvens frx-nuvens-dia" : tipo === "chefe" ? "frx-nuvens frx-nuvens-chefe" : "";
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      {nuvens && <div className={nuvens} />}
      <canvas ref={ref} className="absolute inset-0 h-full w-full" />
      {(tipo === "chuva" || tipo === "tempestade") && <div className="frx-nevoa" />}
    </div>
  );
}
