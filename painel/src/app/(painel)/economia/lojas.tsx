"use client";

import { useMemo, useState, useTransition } from "react";
import { CampoMoedas, IconeItem, SeletorItem, useMapaItens } from "@/components/itens";
import { salvarLoja, type ItemLojaDados } from "./actions";

const LOJAS = [
  { id: "MERCADOR", icone: "🛒", titulo: "Mercador", sub: "o jogador COMPRA", ajuda: "O jogador paga o preço e recebe o item.", cor: "#7cd67c" },
  { id: "COMPRADOR", icone: "⚖️", titulo: "Comprador", sub: "o jogador VENDE", ajuda: "O jogador entrega o item e recebe o preço.", cor: "#5dade2" },
] as const;

const base = (id: string) => id.split("#")[0];

function Loja({ cfg, inicial, outra }: { cfg: (typeof LOJAS)[number]; inicial: ItemLojaDados[]; outra: ItemLojaDados[] }) {
  const [itens, setItens] = useState(inicial);
  const [salvo, setSalvo] = useState(inicial);
  const [busca, setBusca] = useState("");
  const [trocar, setTrocar] = useState<number | null>(null);
  const [adicionar, setAdicionar] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [salvando, iniciar] = useTransition();
  const mapa = useMapaItens();
  const alterado = JSON.stringify(itens) !== JSON.stringify(salvo);
  const nome = (id: string) => mapa.get(base(id))?.nome ?? id.split(":")[1]?.replace(/_/g, " ") ?? id;

  // Preço unitário da outra loja, para avisar de lucro fácil (comprar barato num e vender caro no outro).
  const outraUnit = useMemo(() => {
    const m = new Map<string, number>();
    for (const o of outra) if (o.ativo) m.set(o.item, o.preco / o.qtd);
    return m;
  }, [outra]);
  const arbitragem = (i: ItemLojaDados) => {
    const u = outraUnit.get(i.item);
    if (u === undefined || !i.ativo) return null;
    const meu = i.preco / i.qtd;
    return cfg.id === "MERCADOR" ? (meu < u ? u : null) : (meu > u ? u : null);
  };

  const mudar = (idx: number, p: Partial<ItemLojaDados>) => setItens((l) => l.map((x, j) => (j === idx ? { ...x, ...p } : x)));
  const mover = (idx: number, d: number) => setItens((l) => {
    const n = [...l]; const k = idx + d; if (k < 0 || k >= n.length) return l;
    [n[idx], n[k]] = [n[k], n[idx]]; return n;
  });
  const salvar = () => iniciar(async () => {
    const r = await salvarLoja(cfg.id, itens);
    if (r.ok) { setSalvo(itens); setMsg({ ok: true, t: "Loja salva ✓" }); } else setMsg({ ok: false, t: r.erro ?? "Erro" });
  });

  const q = busca.trim().toLowerCase();
  const avisos = itens.filter((i) => arbitragem(i) !== null).length;

  return (
    <section className="flex flex-col rounded-xl border border-pedra-700 bg-pedra-900">
      <div className="flex items-center gap-3 border-b border-pedra-700 p-4">
        <span className="grid h-11 w-11 place-items-center rounded-lg text-2xl" style={{ background: `${cfg.cor}22` }}>{cfg.icone}</span>
        <div>
          <h2 className="font-titulo text-xl" style={{ color: cfg.cor }}>{cfg.titulo} <span className="text-sm text-pedra-500">— {cfg.sub}</span></h2>
          <p className="text-xs text-pedra-500">{cfg.ajuda} {itens.length} itens · {itens.filter((i) => i.ativo).length} à venda</p>
        </div>
      </div>

      {avisos > 0 && (
        <p className="mx-4 mt-3 rounded-md border border-red-500/40 bg-red-900/20 px-3 py-2 text-xs text-red-300">
          ⚠ {avisos} item(ns) dá(ão) lucro infinito: o jogador compra no Mercador mais barato do que o Comprador paga. Ajuste os preços.
        </p>
      )}

      <div className="flex items-center gap-2 px-4 pt-3">
        <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="🔎 Filtrar itens…" className="w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-sm outline-none focus:border-ouro" />
        <button onClick={() => setAdicionar(true)} className="shrink-0 rounded-md border border-ouro/60 px-3 py-1.5 text-sm text-ouro hover:bg-ouro/15">+ Item</button>
      </div>

      <div className="max-h-[560px] flex-1 space-y-1.5 overflow-y-auto p-4">
        {!itens.length && <p className="py-8 text-center text-sm text-pedra-500">Loja vazia. Clique em “+ Item”.</p>}
        {itens.map((i, idx) => {
          if (q && !nome(i.item).toLowerCase().includes(q) && !i.item.includes(q)) return null;
          const arb = arbitragem(i);
          return (
            <div key={idx} className={`grid grid-cols-[18px_44px_1fr_auto] items-center gap-2 rounded-lg border p-2 ${arb !== null ? "border-red-500/50 bg-red-950/20" : "border-pedra-800 bg-pedra-950/50"} ${i.ativo ? "" : "opacity-50"}`}>
              <div className="flex flex-col text-[10px] leading-none text-pedra-500">
                <button onClick={() => mover(idx, -1)} className="hover:text-ouro" title="Subir">▲</button>
                <button onClick={() => mover(idx, 1)} className="hover:text-ouro" title="Descer">▼</button>
              </div>
              <button onClick={() => setTrocar(idx)} title="Trocar item" className="relative grid h-11 w-11 place-items-center rounded-md border border-pedra-700 bg-pedra-900 hover:border-ouro">
                <IconeItem id={i.item} tam={32} />
                {i.qtd > 1 && <span className="absolute bottom-0 right-0.5 text-xs font-bold text-white [text-shadow:1px_1px_0_#000]">{i.qtd}</span>}
              </button>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold" title={i.item}>{nome(i.item)}</p>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                  <label className="flex items-center gap-1 text-pedra-500">qtd
                    <input type="number" min={1} max={64} value={i.qtd} onChange={(e) => mudar(idx, { qtd: Math.min(64, Math.max(1, +e.target.value || 1)) })} className="w-12 rounded border border-pedra-700 bg-pedra-950 px-1 py-0.5 text-pergaminho" />
                  </label>
                  <CampoMoedas cobre={i.preco} onChange={(c) => mudar(idx, { preco: Math.max(1, c) })} />
                </div>
                <p className="mt-0.5 text-[11px] text-pedra-500">
                  {i.qtd > 1 && <>≈ {(i.preco / i.qtd).toFixed(1)} cobre cada · </>}
                  {arb !== null && <span className="text-red-300">{cfg.id === "MERCADOR" ? "Comprador paga" : "Mercador vende por"} {arb.toFixed(1)} cada!</span>}
                </p>
              </div>
              <div className="flex flex-col items-end gap-1.5">
                <button onClick={() => mudar(idx, { ativo: !i.ativo })} title={i.ativo ? "À venda" : "Pausado"}>
                  <span className={`relative block h-4 w-8 rounded-full ${i.ativo ? "bg-green-500" : "bg-pedra-700"}`}><span className={`absolute top-0.5 h-3 w-3 rounded-full bg-white transition-all ${i.ativo ? "left-[18px]" : "left-0.5"}`} /></span>
                </button>
                <button onClick={() => setItens((l) => l.filter((_, j) => j !== idx))} className="text-xs text-red-300 hover:text-red-400" title="Remover">🗑</button>
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex items-center gap-3 border-t border-pedra-700 p-3">
        {msg ? <span className={`text-xs ${msg.ok ? "text-green-400" : "text-red-400"}`}>{msg.t}</span> : <span className="text-xs text-pedra-500">{alterado ? "Alterações não salvas" : "Tudo salvo"}</span>}
        {alterado && <button onClick={() => { setItens(salvo); setMsg(null); }} className="ml-auto text-xs text-pedra-500 hover:text-pergaminho">Desfazer</button>}
        <button onClick={salvar} disabled={!alterado || salvando} className={`${alterado ? "" : "ml-auto"} rounded-md bg-ouro px-4 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-40`}>
          {salvando ? "Salvando…" : "💾 Salvar loja"}
        </button>
      </div>

      {trocar !== null && <SeletorItem titulo="Trocar item" aoEscolher={(id) => { mudar(trocar, { item: id }); setTrocar(null); setMsg(null); }} aoFechar={() => setTrocar(null)} />}
      {adicionar && (
        <SeletorItem titulo={`Adicionar ao ${cfg.titulo}`} aoFechar={() => setAdicionar(false)}
          aoEscolher={(id) => { setItens((l) => [...l, { item: id, qtd: 1, preco: outraUnit.has(id) ? Math.max(1, Math.round(outraUnit.get(id)! * (cfg.id === "MERCADOR" ? 2 : 0.5))) : 10, ativo: true }]); setAdicionar(false); setMsg(null); }} />
      )}
    </section>
  );
}

export function Lojas({ mercador, comprador }: { mercador: ItemLojaDados[]; comprador: ItemLojaDados[] }) {
  return (
    <>
      <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
        <h2 className="font-titulo text-2xl">Lojas das capitais</h2>
        <p className="text-xs text-pedra-500">10 cobre = 1 prata · 10 prata = 1 ouro · clique no ícone para trocar o item · ▲▼ muda a ordem</p>
      </div>
      <div className="grid gap-5 xl:grid-cols-2">
        <Loja cfg={LOJAS[0]} inicial={mercador} outra={comprador} />
        <Loja cfg={LOJAS[1]} inicial={comprador} outra={mercador} />
      </div>
    </>
  );
}
