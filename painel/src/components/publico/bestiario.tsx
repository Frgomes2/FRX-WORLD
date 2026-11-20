"use client";

import { useEffect, useRef, useState } from "react";
import type { CriaturaPublica } from "@/lib/publico";

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];

/** Corpo de frente (16 × 32 pixels) montado a partir de uma skin 64×64 no formato de jogador, com a segunda camada por cima. */
function Corpo({ codigo, slim, tam = 5 }: { codigo: string; slim: boolean; tam?: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [falhou, setFalhou] = useState(false);
  useEffect(() => {
    let vivo = true;
    const im = new Image();
    im.onload = () => {
      const cv = ref.current;
      if (!vivo || !cv) return;
      const x = cv.getContext("2d")!;
      x.imageSmoothingEnabled = false;
      x.clearRect(0, 0, 16, 32);
      const b = slim ? 3 : 4;   // largura do braço
      // [origem x, y, largura, altura, destino x, y] — camada de base e, 2ª lista, a de cima
      const base: number[][] = [[8, 8, 8, 8, 4, 0], [20, 20, 8, 12, 4, 8], [44, 20, b, 12, 4 - b, 8], [36, 52, b, 12, 12, 8], [4, 20, 4, 12, 4, 20], [20, 52, 4, 12, 8, 20]];
      const cima: number[][] = [[40, 8, 8, 8, 4, 0], [20, 36, 8, 12, 4, 8], [44, 36, b, 12, 4 - b, 8], [52, 52, b, 12, 12, 8], [4, 36, 4, 12, 4, 20], [4, 52, 4, 12, 8, 20]];
      for (const [sx, sy, w, h, dx, dy] of [...base, ...cima]) x.drawImage(im, sx, sy, w, h, dx, dy, w, h);
    };
    im.onerror = () => { if (vivo) setFalhou(true); };
    im.src = `/api/publico/criatura-skin?c=${encodeURIComponent(codigo)}`;
    return () => { vivo = false; };
  }, [codigo, slim]);
  if (falhou) return <span className="text-5xl">☠</span>;
  return <canvas ref={ref} width={16} height={32} style={{ width: 16 * tam, height: 32 * tam, imageRendering: "pixelated" }} aria-hidden />;
}

function Cartao({ c }: { c: CriaturaPublica }) {
  const faixa = `Rank ${RANKS[c.rankMin]}${c.rankMax !== c.rankMin ? ` a ${RANKS[c.rankMax]}` : ""}`;
  if (!c.descoberto) {
    return (
      <div className="frx-bicho frx-bicho-oculto">
        <div className="frx-bicho-retrato"><span className="frx-bicho-sombra" aria-hidden>?</span></div>
        <p className="frx-bicho-nome">? ? ?</p>
        <p className="frx-bicho-linha">Não descoberto</p>
        <p className="frx-bicho-linha">{faixa}</p>
        <p className="frx-bicho-dica">{c.locais.length ? `Dizem que aparece em: ${c.locais.join(", ")}` : "Ninguém sabe onde aparece"}</p>
      </div>
    );
  }
  return (
    <div className="frx-bicho">
      <div className="frx-bicho-retrato">{c.skin ? <Corpo codigo={c.codigo} slim={c.slim} /> : <span className="text-5xl" aria-hidden>{c.tipo === "chefe" ? "☠" : "👹"}</span>}</div>
      <p className="frx-bicho-nome">{c.tipo === "chefe" ? "☠ " : ""}{c.nome}</p>
      <p className="frx-bicho-linha">{faixa} · derrotado {c.derrotas}×</p>
      <p className="frx-bicho-linha" title={c.primeiro ? "Primeiro personagem do servidor a derrotar esta criatura" : "A primeira derrota aconteceu antes de o servidor passar a registrar quem foi"}>
        {c.primeiro
          ? <>🏆 Primeiro: <b className="text-ouro">{c.primeiro}</b>{c.primeiroEm ? ` · ${new Date(c.primeiroEm).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit" })}` : ""}</>
          : <span className="opacity-70">🏆 Primeiro: sem registro</span>}
      </p>
      {c.descricao && <p className="frx-bicho-historia">{c.descricao}</p>}
      {!!c.golpes.length && <p className="frx-bicho-golpes">{c.golpes.map((g) => <span key={g.nome} title={`${g.nome}: ${g.desc}`}>{g.icone}</span>)}</p>}
      {!!c.locais.length && <p className="frx-bicho-dica">{c.locais.join(" · ")}</p>}
    </div>
  );
}

/** Bestiário: os chefes e monstros próprios do servidor. Cada um fica como "não descoberto" até alguém derrotá-lo. */
export function Bestiario({ criaturas }: { criaturas: CriaturaPublica[] }) {
  const [aba, setAba] = useState<"chefe" | "monstro">("chefe");
  if (!criaturas.length) return <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-5 text-sm text-pedra-500">O bestiário ainda está em branco.</p>;
  const lista = criaturas.filter((c) => c.tipo === aba);
  const total = (t: "chefe" | "monstro") => criaturas.filter((c) => c.tipo === t);
  const botao = (t: "chefe" | "monstro", nome: string) => {
    const todos = total(t);
    return (
      <button type="button" onClick={() => setAba(t)} className={`rounded-md border px-3 py-1.5 text-sm ${aba === t ? "border-ouro bg-pedra-700 text-ouro" : "border-pedra-700 hover:border-ouro-escuro"}`}>
        {nome} <span className="text-xs opacity-70">{todos.filter((c) => c.descoberto).length} de {todos.length}</span>
      </button>
    );
  };
  return (
    <>
      <div className="mb-3 flex flex-wrap gap-2">{botao("chefe", "☠ Chefes")}{!!total("monstro").length && botao("monstro", "👹 Monstros")}</div>
      {(() => {
        const todos = total(aba), achados = todos.filter((c) => c.descoberto).length, pct = todos.length ? Math.round((achados / todos.length) * 100) : 0;
        return (
          <div className="mb-4 rounded-xl border border-pedra-700 bg-pedra-900 px-4 py-3">
            <p className="mb-1.5 flex items-baseline justify-between text-sm">
              <span>Coleção do servidor: <b className="text-ouro">{achados}</b> de {todos.length} {aba === "chefe" ? "chefes" : "monstros"} descobertos</span>
              <span className="font-titulo text-xl text-ouro">{pct}%</span>
            </p>
            <div className="h-2.5 overflow-hidden rounded-full bg-pedra-950"><div key={aba} className="frx-enche h-full rounded-full bg-gradient-to-r from-ouro-escuro to-ouro" style={{ width: `${pct}%` }} /></div>
            {achados < todos.length && <p className="mt-1.5 text-xs text-pedra-500">Os que faltam aparecem como «?». Derrote um para revelar o nome, a história e os golpes dele para todo mundo.</p>}
          </div>
        );
      })()}
      {lista.length ? <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">{lista.map((c, i) => <Cartao key={c.descoberto ? c.codigo : `oculto-${i}`} c={c} />)}</div>
        : <p className="text-sm text-pedra-500">Nenhuma criatura deste tipo ainda.</p>}
    </>
  );
}
