/* Efeitos visuais leves e determinísticos (mesmo HTML no servidor e no navegador). */

const PONTOS = Array.from({ length: 16 }, (_, i) => {
  const r = (n: number) => ((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1 + 1) % 1;
  return { left: `${Math.round(r(1) * 100)}%`, top: `${Math.round(40 + r(2) * 60)}%`, d: `${7 + Math.round(r(3) * 8)}s`, a: `${(r(4) * 9).toFixed(1)}s`,
    x: `${Math.round(-40 + r(5) * 80)}px`, y: `${Math.round(-60 - r(6) * 80)}px`, cor: r(7) > 0.75 ? "#6ee7b7" : undefined };
});

/** Vaga-lumes subindo devagar no fundo de um bloco com position: relative. `verde` mistura alguns verdes (expedições). */
export function Vagalumes({ quantos = 12, verde = false }: { quantos?: number; verde?: boolean }) {
  return (
    <div className="frx-vagalumes" aria-hidden="true">
      {PONTOS.slice(0, quantos).map((p, i) => (
        <i key={i} style={{ left: p.left, top: p.top, ["--d" as string]: p.d, ["--a" as string]: p.a, ["--x" as string]: p.x, ["--y" as string]: p.y,
          ...(verde && p.cor ? { background: p.cor, boxShadow: `0 0 8px 2px ${p.cor}aa` } : {}) }} />
      ))}
    </div>
  );
}
