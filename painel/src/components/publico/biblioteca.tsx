"use client";

import { useCallback, useEffect, useState } from "react";
import type { Bloco, Livro, Pagina } from "@/lib/livros";

function Conteudo({ b }: { b: Bloco }) {
  if (b.t === "p") return <p className="leading-relaxed">{b.texto}</p>;
  if (b.t === "nota") return <p className="rounded border-l-4 border-[#8a6516] bg-[#8a6516]/15 px-3 py-2 text-[13px] leading-relaxed">{b.texto}</p>;
  if (b.t === "lista") return <ul className="list-disc space-y-1.5 pl-5 leading-relaxed">{b.itens.map((i) => <li key={i}>{i}</li>)}</ul>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-[13px]">
        <thead><tr>{b.cab.map((c) => <th key={c} className="border-b-2 border-[#5a3a1e]/60 px-2 py-1 text-left font-titulo">{c}</th>)}</tr></thead>
        <tbody>{b.linhas.map((l, i) => <tr key={i}>{l.map((c, j) => <td key={j} className="border-b border-[#5a3a1e]/25 px-2 py-1 align-top">{c}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}

function Folha({ pagina, numero, total, lado }: { pagina: Pagina | null; numero: number; total: number; lado: "esq" | "dir" }) {
  return (
    <div className={`frx-folha relative flex min-h-[420px] flex-1 flex-col px-6 py-6 text-[14px] text-[#2a2118] sm:px-8 ${lado === "esq" ? "frx-folha-esq" : "frx-folha-dir"}`}>
      {pagina ? (
        <>
          <h3 className="mb-3 border-b border-[#5a3a1e]/40 pb-2 font-titulo text-xl text-[#5a2f0e]">{pagina.titulo}</h3>
          <div className="flex-1 space-y-3">{pagina.blocos.map((b, i) => <Conteudo key={i} b={b} />)}</div>
          <p className={`mt-4 text-xs text-[#5a3a1e]/70 ${lado === "esq" ? "text-left" : "text-right"}`}>{numero} / {total}</p>
        </>
      ) : <div className="flex flex-1 items-center justify-center font-titulo text-2xl text-[#5a3a1e]/40">Fim</div>}
    </div>
  );
}

/** Leitor: duas páginas lado a lado no computador, uma por vez no celular. Setas do teclado folheiam. */
function Leitor({ livro, fechar }: { livro: Livro; fechar: () => void }) {
  const [i, setI] = useState(0);
  const [duplo, setDuplo] = useState(true);
  const [virando, setVirando] = useState<"" | "frente" | "tras">("");
  const passo = duplo ? 2 : 1;
  const total = livro.paginas.length;
  const ir = useCallback((d: 1 | -1) => {
    setI((atual) => {
      const novo = atual + d * passo;
      if (novo < 0 || novo >= total) return atual;
      setVirando(d === 1 ? "frente" : "tras");
      return novo;
    });
  }, [passo, total]);

  useEffect(() => {
    const medir = () => setDuplo(window.innerWidth >= 900);
    medir();
    window.addEventListener("resize", medir);
    const tecla = (e: KeyboardEvent) => { if (e.key === "Escape") fechar(); else if (e.key === "ArrowRight") ir(1); else if (e.key === "ArrowLeft") ir(-1); };
    window.addEventListener("keydown", tecla);
    document.body.style.overflow = "hidden";
    return () => { window.removeEventListener("resize", medir); window.removeEventListener("keydown", tecla); document.body.style.overflow = ""; };
  }, [fechar, ir]);
  useEffect(() => { if (!virando) return; const t = setTimeout(() => setVirando(""), 450); return () => clearTimeout(t); }, [virando, i]);

  const base = duplo ? i - (i % 2) : i;
  const botao = "rounded-md border border-pedra-700 bg-pedra-900 px-4 py-2 text-sm hover:border-ouro disabled:cursor-not-allowed disabled:opacity-30";
  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center overflow-y-auto bg-black/85 p-3 sm:p-6" role="dialog" aria-modal="true" aria-label={livro.titulo} onClick={fechar}>
      <div className="w-full max-w-5xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="min-w-0 truncate font-titulo text-2xl text-ouro"><span className="mr-2">{livro.icone}</span>{livro.titulo}<span className="ml-3 hidden text-sm text-pedra-500 sm:inline">{livro.subtitulo}</span></p>
          <button type="button" onClick={fechar} className={botao}>Fechar ✕</button>
        </div>
        <div className="frx-livro-aberto relative p-2.5 sm:p-4" style={{ "--cor": livro.cor } as React.CSSProperties}>
          {duplo && <span className="frx-aberto-fita" aria-hidden />}
          <div key={base} className={`frx-miolo flex overflow-hidden ${virando === "frente" ? "frx-virar-frente" : virando === "tras" ? "frx-virar-tras" : ""}`}>
            <Folha pagina={livro.paginas[base] ?? null} numero={base + 1} total={total} lado="esq" />
            {duplo && <Folha pagina={livro.paginas[base + 1] ?? null} numero={base + 2} total={total} lado="dir" />}
          </div>
        </div>
        <div className="mt-3 flex items-center justify-between gap-3">
          <button type="button" className={botao} disabled={base <= 0} onClick={() => ir(-1)}>← Página anterior</button>
          <span className="text-xs text-pedra-500">use as setas do teclado</span>
          <button type="button" className={botao} disabled={base + passo >= total} onClick={() => ir(1)}>Próxima página →</button>
        </div>
      </div>
    </div>
  );
}

/**
 * Livro em pixel art (grade de 16 × 20, desenho próprio): capa na cor do livro (--cor), lombada com duas faixas douradas,
 * fecho, janela escura para o ícone e as páginas aparecendo embaixo.
 */
function LivroPixel() {
  const mistura = (pct: number, com: string) => `color-mix(in srgb, var(--cor) ${pct}%, ${com})`;
  const r = (x: number, y: number, w: number, h: number, fill: string, k: string) => <rect key={k} x={x} y={y} width={w} height={h} style={{ fill }} />;
  return (
    <svg viewBox="0 0 16 20" className="frx-livro-pixel" shapeRendering="crispEdges" aria-hidden>
      {r(2, 0, 12, 1, mistura(35, "black"), "t")}{r(1, 1, 14, 18, mistura(35, "black"), "c")}{r(2, 19, 12, 1, mistura(35, "black"), "b")}
      {r(2, 1, 12, 16, "var(--cor)", "capa")}
      {r(2, 1, 2, 16, mistura(60, "black"), "lombada")}
      {r(4, 1, 9, 1, mistura(70, "white"), "luz")}{r(4, 2, 1, 3, mistura(70, "white"), "luz2")}
      {r(13, 1, 1, 16, mistura(75, "black"), "sombra")}{r(4, 16, 9, 1, mistura(75, "black"), "sombra2")}
      {r(2, 3, 2, 1, "#f5d04a", "f1")}{r(2, 4, 2, 1, "#b8860b", "f1s")}{r(2, 13, 2, 1, "#f5d04a", "f2")}{r(2, 14, 2, 1, "#b8860b", "f2s")}
      {r(5, 4, 7, 1, mistura(45, "black"), "j1")}{r(5, 5, 7, 6, mistura(30, "black"), "janela")}{r(5, 11, 7, 1, mistura(85, "white"), "j2")}
      {r(12, 7, 2, 2, "#f5d04a", "fecho")}{r(13, 8, 1, 1, "#b8860b", "fechos")}
      {r(6, 13, 5, 1, "#f5d04a", "linha")}{r(7, 14, 3, 1, "#b8860b", "linha2")}
      {r(3, 17, 11, 1, "#efe4c3", "pag")}{r(3, 18, 11, 1, "#c9b98c", "pag2")}{r(2, 17, 1, 2, mistura(60, "black"), "pe")}
    </svg>
  );
}

/** Estante com os livros do servidor: clicar abre e dá para folhear. */
export function Biblioteca({ livros }: { livros: Livro[] }) {
  const [aberto, setAberto] = useState<Livro | null>(null);
  const fechar = useCallback(() => setAberto(null), []);
  if (!livros.length) return <p className="text-sm text-pedra-500">A biblioteca está fechada no momento. Volte daqui a pouco.</p>;
  return (
    <>
      {(["Mundo", "Classes"] as const).map((grupo) => (
        <div key={grupo} className="mb-6">
          <p className="mb-3 text-xs uppercase tracking-[0.25em] text-pedra-500">{grupo === "Mundo" ? "Sobre o mundo" : "Livros das classes"}</p>
          <div className="frx-estante flex flex-wrap px-3 sm:px-5">
            {livros.filter((l) => l.grupo === grupo).map((l) => (
              <button key={l.id} type="button" onClick={() => setAberto(l)} title={`Abrir ${l.titulo} (${l.paginas.length} páginas)`} className="frx-nicho" style={{ "--cor": l.cor } as React.CSSProperties}>
                <span className="frx-livro">
                  <LivroPixel />
                  <span className="frx-livro-icone" aria-hidden>{l.icone}</span>
                </span>
                <span className="frx-placa">
                  <span className="frx-placa-titulo">{l.titulo}</span>
                  <span className="frx-placa-sub">{l.subtitulo}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      ))}
      {aberto && <Leitor livro={aberto} fechar={fechar} />}
    </>
  );
}
