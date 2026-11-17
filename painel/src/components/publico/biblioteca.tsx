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
        <div className="frx-livro-aberto rounded-lg p-2 shadow-2xl sm:p-3" style={{ background: livro.cor }}>
          <div key={base} className={`flex overflow-hidden rounded ${virando === "frente" ? "frx-virar-frente" : virando === "tras" ? "frx-virar-tras" : ""}`}>
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
          <div className="frx-estante flex flex-wrap items-end gap-4 rounded-xl border border-pedra-700 px-4 pb-0 pt-5">
            {livros.filter((l) => l.grupo === grupo).map((l) => (
              <button key={l.id} type="button" onClick={() => setAberto(l)} title={`Abrir ${l.titulo}`}
                className="frx-capa group relative mb-3 flex h-44 w-28 flex-col justify-between rounded-r-md rounded-l-sm p-3 text-left shadow-lg transition hover:-translate-y-2 sm:h-48 sm:w-32"
                style={{ background: `linear-gradient(90deg, rgba(0,0,0,.45) 0, rgba(0,0,0,.15) 8%, transparent 14%), ${l.cor}` }}>
                <span className="text-3xl drop-shadow">{l.icone}</span>
                <span>
                  <span className="block font-titulo text-[15px] leading-tight text-[#f3e3b3]">{l.titulo}</span>
                  <span className="mt-1 block text-[10px] leading-tight text-[#f3e3b3]/70">{l.subtitulo}</span>
                  <span className="mt-2 block text-[10px] text-[#f3e3b3]/50">{l.paginas.length} páginas</span>
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
