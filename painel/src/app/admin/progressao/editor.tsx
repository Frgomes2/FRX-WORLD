"use client";

import { useEffect, useMemo, useState, useTransition } from "react";
import type { ConfigJogo } from "@/lib/config-jogo";
import { salvarProgressao } from "./actions";
import { NOMES_DIMENSOES } from "@/lib/dimensoes";
import { IconeCriatura, SeletorCriaturas, useCriaturas } from "@/components/criaturas";

type Dados = Pick<ConfigJogo, "progressao" | "ranks" | "morte" | "ferido" | "invasoes" | "chefeMundial" | "floresta" | "zonas">;
type SecaoId = "xp" | "ranks" | "morte" | "invasoes" | "chefe" | "floresta" | "zonas";

const SECOES: { id: SecaoId; icone: string; nome: string; resumo: string; chaves: (keyof Dados)[] }[] = [
  { id: "xp", icone: "📈", nome: "Curva de XP", resumo: "Quanto custa cada nível", chaves: ["progressao"] },
  { id: "ranks", icone: "🎖️", nome: "Ranks", resumo: "F, E, D… até S", chaves: ["ranks"] },
  { id: "morte", icone: "💀", nome: "Morte", resumo: "O que se perde ao morrer", chaves: ["morte", "ferido"] },
  { id: "zonas", icone: "🗺️", nome: "Zonas de perigo", resumo: "Monstros mais fortes longe do spawn", chaves: ["zonas"] },
  { id: "invasoes", icone: "⚔️", nome: "Invasões", resumo: "Hordas atacando as capitais", chaves: ["invasoes"] },
  { id: "chefe", icone: "☠️", nome: "Chefe mundial", resumo: "Evento marcado com chefão", chaves: ["chefeMundial"] },
  { id: "floresta", icone: "🌲", nome: "Floresta sombria", resumo: "Mata fechada em volta da capital", chaves: ["floresta"] },
];

const RANK_COR: Record<string, string> = { F: "#8a7d6a", E: "#7cd67c", D: "#5dade2", C: "#c58cf0", B: "#f1c40f", A: "#ff8a65", S: "#e05a4a" };
const DIAS = ["SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM", "TODO"] as const;
const NOME_DIA: Record<string, string> = { SEG: "Segunda", TER: "Terça", QUA: "Quarta", QUI: "Quinta", SEX: "Sexta", SAB: "Sábado", DOM: "Domingo", TODO: "Todo dia" };

const fmt = (n: number) => Math.round(n).toLocaleString("pt-BR");
const igual = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
const xpPara = (d: Dados, n: number) => Math.max(1, Math.round(d.progressao.xpBase * Math.pow(n, d.progressao.xpExpoente)));

// ------------------------------------------------------------------ peças de formulário

function Cartao({ titulo, ajuda, children, className = "" }: { titulo?: string; ajuda?: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-pedra-700 bg-pedra-900/80 p-4 ${className}`}>
      {titulo && <p className="font-titulo text-lg text-ouro">{titulo}</p>}
      {ajuda && <p className="mb-3 text-xs text-pedra-500">{ajuda}</p>}
      {children}
    </div>
  );
}

function Numero({ rotulo, ajuda, valor, onChange, min, max, passo = 1, unidade, deslizante = true }: {
  rotulo: string; ajuda?: string; valor: number; onChange: (v: number) => void; min: number; max: number; passo?: number; unidade?: string; deslizante?: boolean;
}) {
  const fora = valor < min || valor > max || Number.isNaN(valor);
  return (
    <label className="block">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-sm">{rotulo}</span>
        <span className="flex items-center gap-1">
          <input type="number" value={Number.isNaN(valor) ? "" : valor} step={passo} min={min} max={max}
            onChange={(e) => onChange(e.target.value === "" ? NaN : Number(e.target.value))}
            className={`w-24 rounded-md border bg-pedra-950 px-2 py-0.5 text-right font-mono text-sm outline-none focus:border-ouro ${fora ? "border-red-500" : "border-pedra-700"}`} />
          {unidade && <span className="w-10 text-xs text-pedra-500">{unidade}</span>}
        </span>
      </div>
      {deslizante && (
        <input type="range" min={min} max={max} step={passo} value={Number.isNaN(valor) ? min : Math.min(max, Math.max(min, valor))}
          onChange={(e) => onChange(Number(e.target.value))} className="mt-1 w-full accent-yellow-500" />
      )}
      {ajuda && <span className="block text-xs text-pedra-500">{ajuda}</span>}
      {fora && <span className="block text-xs text-red-400">Use um valor entre {min} e {max}.</span>}
    </label>
  );
}

function Chave({ rotulo, ajuda, valor, onChange }: { rotulo: string; ajuda?: string; valor: boolean; onChange: (v: boolean) => void }) {
  return (
    <button type="button" onClick={() => onChange(!valor)} className="flex w-full items-center gap-3 rounded-lg border border-pedra-700 bg-pedra-950/50 px-3 py-2 text-left hover:border-ouro/50">
      <span className={`relative h-6 w-11 shrink-0 rounded-full transition ${valor ? "bg-green-500" : "bg-pedra-700"}`}>
        <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all ${valor ? "left-[22px]" : "left-0.5"}`} />
      </span>
      <span>
        <span className="block text-sm">{rotulo}</span>
        {ajuda && <span className="block text-xs text-pedra-500">{ajuda}</span>}
      </span>
    </button>
  );
}

/** Lista de criaturas como etiquetas com ícone; escolhe numa lista com imagens ou digita o id. */
function Etiquetas({ rotulo, ajuda, valores, onChange, exemplo, filtro = "monstros" }: {
  rotulo: string; ajuda?: string; valores: string[]; onChange: (v: string[]) => void; exemplo: string; filtro?: string;
}) {
  const [novo, setNovo] = useState("");
  const [abrir, setAbrir] = useState(false);
  const todas = useCriaturas();
  const porId = useMemo(() => new Map((todas ?? []).map((c) => [c.id, c])), [todas]);
  const add = () => {
    const ids = novo.split(/[,\s]+/).map((x) => x.trim().toLowerCase()).filter(Boolean).map((x) => (x.includes(":") ? x : `minecraft:${x}`));
    if (ids.length) onChange([...valores, ...ids.filter((i) => !valores.includes(i))]);
    setNovo("");
  };
  return (
    <div>
      <div className="flex items-center gap-2">
        <p className="text-sm">{rotulo} <span className="text-xs text-pedra-500">({valores.length})</span></p>
        <button type="button" onClick={() => setAbrir(true)} className="rounded-full border border-ouro/60 bg-ouro/10 px-3 py-0.5 text-xs text-ouro hover:bg-ouro/20">🔍 Escolher na lista</button>
      </div>
      {ajuda && <p className="text-xs text-pedra-500">{ajuda}</p>}
      <div className="mt-1.5 flex flex-wrap gap-1.5">
        {valores.map((v) => {
          const c = porId.get(v);
          const desconhecido = todas && !c;
          return (
            <span key={v} title={desconhecido ? `${v} — não existe no modpack atual` : v}
              className={`group flex items-center gap-1.5 rounded-full border py-0.5 pl-1 pr-1 text-xs ${desconhecido ? "border-red-500/60 bg-red-950/30" : "border-pedra-700 bg-pedra-950"}`}>
              <IconeCriatura c={c} id={v} tam={18} />
              <span>{c ? c.nome : v.split(":")[1]?.replace(/_/g, " ")}</span>
              {c && c.mod !== "Minecraft" && <span className="text-[10px] text-purple-300/70">{c.mod}</span>}
              {desconhecido && <span className="text-[10px] text-red-300">não encontrado</span>}
              <button type="button" onClick={() => onChange(valores.filter((x) => x !== v))} className="grid h-4 w-4 place-items-center rounded-full text-pedra-500 hover:bg-red-500/30 hover:text-red-200">×</button>
            </span>
          );
        })}
        <span className="flex items-center gap-1">
          <input value={novo} onChange={(e) => setNovo(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); add(); } }}
            placeholder={`ou digite o id: ${exemplo}`} className="w-52 rounded-full border border-dashed border-pedra-700 bg-transparent px-2.5 py-0.5 text-xs outline-none focus:border-ouro" />
          {novo && <button type="button" onClick={add} className="rounded-full border border-ouro/50 px-2 text-xs text-ouro hover:bg-ouro/15">+</button>}
        </span>
      </div>
      {abrir && <SeletorCriaturas titulo={rotulo} selecionadas={valores} aoMudar={onChange} aoFechar={() => setAbrir(false)} filtroInicial={filtro} />}
    </div>
  );
}

/** Uma criatura só (ex.: comandante da invasão). */
function UmaCriatura({ rotulo, valor, onChange }: { rotulo: string; valor: string; onChange: (v: string) => void }) {
  const [abrir, setAbrir] = useState(false);
  const todas = useCriaturas();
  const c = todas?.find((x) => x.id === valor);
  return (
    <div>
      <p className="text-sm">{rotulo}</p>
      <button type="button" onClick={() => setAbrir(true)} className="mt-1 flex items-center gap-2 rounded-lg border border-pedra-700 bg-pedra-950 px-3 py-1.5 text-sm hover:border-ouro">
        <IconeCriatura c={c} id={valor} tam={24} /> {c?.nome ?? valor} <span className="text-xs text-pedra-500">· trocar</span>
      </button>
      {abrir && <SeletorCriaturas titulo={rotulo} selecionadas={[valor]} unico aoMudar={(ids) => ids[0] && onChange(ids[0])} aoFechar={() => setAbrir(false)} filtroInicial="todos" />}
    </div>
  );
}

// ------------------------------------------------------------------ prévias

function GraficoXp({ d }: { d: Dados }) {
  const max = d.progressao.nivelMaximo;
  const pontos: { n: number; xp: number; total: number }[] = [];
  let total = 0;
  for (let n = 1; n <= max; n++) { const xp = xpPara(d, n) ; pontos.push({ n, xp, total }); total += xp; }
  const maxTotal = Math.max(1, total);
  const x = (n: number) => ((n - 1) / Math.max(1, max - 1)) * 100;
  const y = (v: number) => 58 - (v / maxTotal) * 56;
  const linha = pontos.map((p) => `${x(p.n).toFixed(2)},${y(p.total).toFixed(2)}`).join(" ");
  return (
    <svg viewBox="0 0 100 60" preserveAspectRatio="none" className="h-40 w-full">
      {d.ranks.map((r) => (
        <g key={r.rank}>
          <line x1={x(r.nivelMin)} x2={x(r.nivelMin)} y1="0" y2="60" stroke={RANK_COR[r.rank]} strokeOpacity="0.35" strokeWidth="0.3" vectorEffect="non-scaling-stroke" />
        </g>
      ))}
      <polyline points={`0,60 ${linha} 100,60`} fill="#d4a93a" fillOpacity="0.15" />
      <polyline points={linha} fill="none" stroke="#d4a93a" strokeWidth="2" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

function proximoChefe(horarios: string[]) {
  const agora = new Date();
  let melhor: Date | null = null;
  for (const h of horarios) {
    const [dia, hm] = h.split(" ");
    const [hh, mm] = (hm ?? "").split(":").map(Number);
    for (let i = 0; i <= 7; i++) {
      const d = new Date(agora); d.setDate(d.getDate() + i); d.setHours(hh, mm, 0, 0);
      const idx = (d.getDay() + 6) % 7; // 0 = segunda
      if (dia !== "TODO" && DIAS[idx] !== dia) continue;
      if (d < agora) continue;
      if (!melhor || d < melhor) melhor = d;
      break;
    }
  }
  return melhor;
}

// ------------------------------------------------------------------ editor

export function EditorProgressao({ inicial, padrao }: { inicial: Dados; padrao: Dados }) {
  const [salvo, setSalvo] = useState(inicial);
  const [d, setD] = useState(inicial);
  const [secao, setSecao] = useState<SecaoId>("xp");
  const [msg, setMsg] = useState<{ ok: boolean; texto: string } | null>(null);
  const [pendente, iniciar] = useTransition();
  const [nivelTeste, setNivelTeste] = useState(25);
  const [jogadoresTeste, setJogadoresTeste] = useState(2);
  const [novoDia, setNovoDia] = useState<string>("SAB");
  const [novaHora, setNovaHora] = useState("20:00");

  const mudou = !igual(d, salvo);
  const sec = SECOES.find((s) => s.id === secao)!;
  const secaoMudou = (s: (typeof SECOES)[number]) => s.chaves.some((k) => !igual(d[k], salvo[k]));

  useEffect(() => {
    const avisar = (e: BeforeUnloadEvent) => { if (mudou) { e.preventDefault(); } };
    window.addEventListener("beforeunload", avisar);
    return () => window.removeEventListener("beforeunload", avisar);
  }, [mudou]);

  const set = <K extends keyof Dados>(k: K, v: Partial<Dados[K]>) => setD((x) => ({ ...x, [k]: Array.isArray(x[k]) ? v : { ...(x[k] as object), ...v } }));

  const salvar = () => iniciar(async () => {
    const r = await salvarProgressao(d);
    setMsg({ ok: r.ok, texto: r.mensagem });
    if (r.ok) setSalvo(d);
  });

  // dados derivados
  const marcos = useMemo(() => {
    const max = d.progressao.nivelMaximo;
    const lista = [5, 10, 20, 35, 50, 70, 90, max].filter((n, i, a) => n <= max && a.indexOf(n) === i);
    let total = 0;
    const res: { n: number; total: number; zumbis: number }[] = [];
    for (let n = 1; n < max; n++) {
      total += xpPara(d, n);
      if (lista.includes(n + 1)) res.push({ n: n + 1, total, zumbis: Math.ceil(total / Math.max(0.0001, 20 * d.progressao.xpMultiplicador)) });
    }
    return res;
  }, [d]);

  const faixaMorte = d.morte.find((m) => nivelTeste <= m.ate) ?? d.morte[d.morte.length - 1];
  const prox = proximoChefe(d.chefeMundial.horarios);
  const inv = d.invasoes;
  const z = d.zonas;
  const fl = d.floresta;

  return (
    <div className="grid gap-5 lg:grid-cols-[230px_1fr]">
      {/* menu das seções */}
      <nav className="h-fit space-y-1 lg:sticky lg:top-4">
        {SECOES.map((s) => (
          <button key={s.id} onClick={() => setSecao(s.id)}
            className={`flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition ${secao === s.id ? "border-ouro bg-ouro/10" : "border-transparent hover:border-pedra-700 hover:bg-pedra-900"}`}>
            <span className="text-2xl">{s.icone}</span>
            <span className="min-w-0 flex-1">
              <span className={`block text-sm ${secao === s.id ? "text-ouro" : ""}`}>{s.nome}</span>
              <span className="block truncate text-xs text-pedra-500">{s.resumo}</span>
            </span>
            {secaoMudou(s) && <span className="h-2 w-2 shrink-0 rounded-full bg-yellow-400" title="Alterado, não salvo" />}
          </button>
        ))}
      </nav>

      <div className="min-w-0 space-y-4 pb-24">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-titulo text-2xl text-ouro">{sec.icone} {sec.nome}</h2>
          <div className="flex gap-2">
            {secaoMudou(sec) && <button onClick={() => setD((x) => { const n = { ...x }; for (const k of sec.chaves) (n as Record<string, unknown>)[k] = salvo[k]; return n; })}
              className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">↶ Desfazer mudanças desta seção</button>}
            <button onClick={() => { if (confirm(`Voltar "${sec.nome}" aos valores padrão?`)) setD((x) => { const n = { ...x }; for (const k of sec.chaves) (n as Record<string, unknown>)[k] = padrao[k]; return n; }); }}
              className="rounded-md border border-pedra-700 px-3 py-1 text-xs text-pedra-500 hover:border-ouro hover:text-pergaminho">⟲ Valores padrão</button>
          </div>
        </div>

        {/* ---------------- curva de XP */}
        {secao === "xp" && (
          <div className="grid gap-4 xl:grid-cols-2">
            <Cartao titulo="Fórmula" ajuda="XP para passar do nível N ao próximo = base × N ^ expoente. Monstros dão XP igual à vida máxima (zumbi = 20).">
              <div className="space-y-4">
                <Numero rotulo="Nível máximo" valor={d.progressao.nivelMaximo} onChange={(v) => set("progressao", { nivelMaximo: v })} min={10} max={300} />
                <Numero rotulo="XP base" ajuda="XP do nível 1 para o 2." valor={d.progressao.xpBase} onChange={(v) => set("progressao", { xpBase: v })} min={1} max={2000} />
                <Numero rotulo="Expoente (dificuldade da subida)" ajuda="1 = cada nível custa igual · 1,5 = vai ficando mais caro · 2+ = bem difícil no fim." valor={d.progressao.xpExpoente} onChange={(v) => set("progressao", { xpExpoente: v })} min={1} max={3} passo={0.05} />
                <Numero rotulo="Multiplicador de XP do servidor" ajuda="2 = fim de semana de XP dobrada." valor={d.progressao.xpMultiplicador} onChange={(v) => set("progressao", { xpMultiplicador: v })} min={0} max={10} passo={0.1} unidade="×" />
              </div>
            </Cartao>
            <Cartao titulo="Como fica" ajuda="XP total acumulada (linha) até o nível máximo; as linhas coloridas são os ranks.">
              <GraficoXp d={d} />
              <div className="overflow-x-auto"><table className="mt-3 w-full text-sm">
                <thead className="text-left text-xs text-pedra-500"><tr><th>Nível</th><th className="text-right">XP total</th><th className="text-right">≈ zumbis</th><th className="text-right">deste nível</th></tr></thead>
                <tbody>
                  {marcos.map((m) => (
                    <tr key={m.n} className="border-t border-pedra-800">
                      <td className="py-1">{m.n}</td>
                      <td className="text-right font-mono">{fmt(m.total)}</td>
                      <td className="text-right font-mono text-pedra-500">{fmt(m.zumbis)}</td>
                      <td className="text-right font-mono text-pedra-500">{fmt(xpPara(d, m.n - 1))}</td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
            </Cartao>
          </div>
        )}

        {/* ---------------- ranks */}
        {secao === "ranks" && (
          <Cartao titulo="Escada de ranks" ajuda="Nível mínimo para cada rank de aventureiro. O rank aparece no chat, no Tab e na janela de status.">
            <div className="mb-5 flex h-10 overflow-hidden rounded-lg">
              {d.ranks.map((r, i) => {
                const fim = i + 1 < d.ranks.length ? d.ranks[i + 1].nivelMin : d.progressao.nivelMaximo + 1;
                return (
                  <div key={r.rank} className="grid place-items-center font-titulo text-lg text-black/80" title={`${r.rank}: nível ${r.nivelMin} a ${fim - 1}`}
                    style={{ width: `${(Math.max(1, fim - r.nivelMin) / d.progressao.nivelMaximo) * 100}%`, background: RANK_COR[r.rank] }}>{r.rank}</div>
                );
              })}
            </div>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
              {d.ranks.map((r, i) => (
                <div key={r.rank} className="rounded-xl border p-3 text-center" style={{ borderColor: RANK_COR[r.rank] + "88" }}>
                  <p className="font-titulo text-3xl" style={{ color: RANK_COR[r.rank] }}>{r.rank}</p>
                  <p className="text-xs text-pedra-500">a partir do nível</p>
                  <input type="number" value={r.nivelMin} disabled={i === 0}
                    onChange={(e) => setD((x) => ({ ...x, ranks: x.ranks.map((y, j) => (j === i ? { ...y, nivelMin: Number(e.target.value) } : y)) }))}
                    className="mt-1 w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-center font-mono disabled:opacity-50" />
                </div>
              ))}
            </div>
          </Cartao>
        )}

        {/* ---------------- morte */}
        {secao === "morte" && (
          <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
            <Cartao titulo="Faixas de punição" ajuda="Cada faixa vale do nível seguinte ao da anterior até o nível indicado.">
              <div className="space-y-3">
                {d.morte.map((m, i) => {
                  const de = i === 0 ? 1 : d.morte[i - 1].ate + 1;
                  const muda = (v: Partial<typeof m>) => setD((x) => ({ ...x, morte: x.morte.map((y, j) => (j === i ? { ...y, ...v } : y)) }));
                  return (
                    <div key={i} className="rounded-xl border border-pedra-700 bg-pedra-950/40 p-3">
                      <div className="mb-2 flex items-center gap-2 text-sm">
                        <span className="rounded-full bg-pedra-700 px-2 py-0.5 text-xs">Faixa {i + 1}</span>
                        <span>Nível {de} até</span>
                        <input type="number" value={m.ate} onChange={(e) => muda({ ate: Number(e.target.value) })} className="w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-0.5 text-center font-mono" />
                        {d.morte.length > 1 && <button onClick={() => setD((x) => ({ ...x, morte: x.morte.filter((_, j) => j !== i) }))} className="ml-auto text-xs text-red-400 hover:underline">remover faixa</button>}
                      </div>
                      <div className="grid gap-3 md:grid-cols-3">
                        <Numero rotulo="XP perdida" valor={m.xpPct} onChange={(v) => muda({ xpPct: v })} min={0} max={100} unidade="%" />
                        <Numero rotulo="Durabilidade perdida" valor={m.durabilidadePct} onChange={(v) => muda({ durabilidadePct: v })} min={0} max={100} unidade="%" />
                        <Numero rotulo="Tempo ferido" valor={m.feridoMin} onChange={(v) => muda({ feridoMin: v })} min={0} max={120} unidade="min" />
                      </div>
                      <div className="mt-2"><Chave rotulo="Pode perder nível" ajuda="Se a XP perdida passar do que tem no nível, desce um nível." valor={m.podePerderNivel} onChange={(v) => muda({ podePerderNivel: v })} /></div>
                    </div>
                  );
                })}
                {d.morte.length < 10 && (
                  <button onClick={() => setD((x) => ({ ...x, morte: [...x.morte, { ...x.morte[x.morte.length - 1], ate: x.morte[x.morte.length - 1].ate + 20 }] }))}
                    className="w-full rounded-xl border border-dashed border-pedra-700 py-2 text-sm text-pedra-500 hover:border-ouro hover:text-ouro">+ nova faixa</button>
                )}
              </div>
            </Cartao>
            <div className="space-y-4">
              <Cartao titulo="Efeito Ferido" ajuda="Depois de morrer, por alguns minutos o jogador fica mais fraco.">
                <div className="space-y-3">
                  <Numero rotulo="Vida máxima a menos" valor={d.ferido.vidaPct} onChange={(v) => set("ferido", { vidaPct: v })} min={0} max={90} unidade="%" />
                  <Numero rotulo="Dano a menos" valor={d.ferido.danoPct} onChange={(v) => set("ferido", { danoPct: v })} min={0} max={90} unidade="%" />
                </div>
              </Cartao>
              <Cartao titulo="Simular uma morte">
                <Numero rotulo="Jogador de nível" valor={nivelTeste} onChange={setNivelTeste} min={1} max={d.progressao.nivelMaximo} />
                <ul className="mt-3 space-y-1 text-sm">
                  <li>📉 perde <b>{faixaMorte.xpPct}%</b> da XP do nível ({fmt((xpPara(d, nivelTeste) * faixaMorte.xpPct) / 100)} XP)</li>
                  <li>{faixaMorte.podePerderNivel ? "⬇️ pode cair de nível" : "🛟 não cai de nível"}</li>
                  <li>🔨 equipamento perde <b>{faixaMorte.durabilidadePct}%</b> da durabilidade</li>
                  <li>🩹 fica ferido por <b>{faixaMorte.feridoMin} min</b> (−{d.ferido.vidaPct}% vida, −{d.ferido.danoPct}% dano)</li>
                </ul>
              </Cartao>
            </div>
          </div>
        )}

        {/* ---------------- zonas */}
        {secao === "zonas" && (
          <div className="grid gap-4 xl:grid-cols-2">
            <Cartao titulo="Regras" ajuda="Quanto mais longe do spawn, mais fortes os monstros (e mais XP e moedas). As outras dimensões têm faixa própria (cartão Dimensões). Nas capitais não nascem monstros.">
              <div className="space-y-4">
                <Numero rotulo="Sobe 1 nível a cada" valor={z.blocosPorNivel} onChange={(v) => set("zonas", { blocosPorNivel: v })} min={50} max={2000} passo={10} unidade="blocos" />
                <Numero rotulo="Nível máximo da zona" valor={z.nivelMax} onChange={(v) => set("zonas", { nivelMax: v })} min={1} max={200} />
                <Numero rotulo="Vida extra por nível" valor={z.vidaPct} onChange={(v) => set("zonas", { vidaPct: v })} min={0} max={100} unidade="%" />
                <Numero rotulo="Dano extra por nível" valor={z.danoPct} onChange={(v) => set("zonas", { danoPct: v })} min={0} max={100} unidade="%" />
                <Numero rotulo="Armadura extra por nível" valor={z.armaduraPorNivel} onChange={(v) => set("zonas", { armaduraPorNivel: v })} min={0} max={5} passo={0.05} />
                <Numero rotulo="Chance de soltar moedas" valor={z.moedaChance} onChange={(v) => set("zonas", { moedaChance: v })} min={0} max={100} unidade="%" />
                <Numero rotulo="Cobre por nível (média)" valor={z.moedaPorNivel} onChange={(v) => set("zonas", { moedaPorNivel: v })} min={0} max={100} passo={0.5} />
              </div>
            </Cartao>
            <Cartao titulo="Monstros com rank" ajuda="Alguns monstros nascem mais fortes, com nome próprio e prêmio melhor. Elite: ★1 a ★5, um traço no nome (Zumbi Berserker). Campeão: muito raro, com título e barra própria. Nas capitais não nascem.">
              <div className="space-y-4">
                <Numero rotulo="Chance de Elite" valor={z.eliteChance} onChange={(v) => set("zonas", { eliteChance: v })} min={0} max={50} passo={0.5} unidade="%" />
                <Numero rotulo="Elite a partir do nível" valor={z.eliteNivelMin} onChange={(v) => set("zonas", { eliteNivelMin: v })} min={1} max={200} />
                <Numero rotulo="Chance de Campeão" valor={z.campeaoChance} onChange={(v) => set("zonas", { campeaoChance: v })} min={0} max={10} passo={0.1} unidade="%" />
                <Numero rotulo="Campeão a partir do nível" valor={z.campeaoNivelMin} onChange={(v) => set("zonas", { campeaoNivelMin: v })} min={1} max={200} />
                <p className="text-xs text-pedra-500">Em 1.000 monstros: cerca de {Math.round(z.eliteChance * 10)} Elites e {Math.round(z.campeaoChance * 10)} Campeões (nas zonas liberadas).</p>
              </div>
            </Cartao>
            <Cartao titulo="Dimensões" ajuda="Cada dimensão é uma etapa da jornada: os monstros de lá nascem entre o nível mínimo e o máximo. Quem entra abaixo do mínimo recebe o aviso de perigo. A superfície segue a distância do spawn.">
              <div className="overflow-x-auto"><table className="w-full text-sm">
                <thead className="text-left text-xs text-pedra-500"><tr><th>Dimensão</th><th className="w-24">Nível mín.</th><th className="w-24">Nível máx.</th><th className="hidden sm:table-cell">Faixa</th><th /></tr></thead>
                <tbody>
                  {Object.entries(z.dimensoes).sort((a, b) => a[1][0] - b[1][0] || a[0].localeCompare(b[0])).map(([id, f]) => {
                    const mudar = (i: 0 | 1, v: number) => set("zonas", { dimensoes: { ...z.dimensoes, [id]: (i === 0 ? [v, f[1]] : [f[0], v]) as [number, number] } });
                    const campoN = "w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-right font-mono outline-none focus:border-ouro";
                    return (
                      <tr key={id} className="border-t border-pedra-800">
                        <td className="py-1.5"><p>{NOMES_DIMENSOES[id] ?? id.split(":")[1]}</p><p className="font-mono text-xs text-pedra-500">{id}</p></td>
                        <td><input type="number" min={1} max={200} value={f[0]} onChange={(e) => mudar(0, Math.max(1, Math.min(200, Math.round(Number(e.target.value) || 1))))} className={campoN} /></td>
                        <td><input type="number" min={1} max={200} value={f[1]} onChange={(e) => mudar(1, Math.max(1, Math.min(200, Math.round(Number(e.target.value) || 1))))} className={`${campoN} ${f[1] < f[0] ? "border-red-500" : ""}`} /></td>
                        <td className="hidden sm:table-cell">
                          <div className="relative h-2 w-40 rounded-full bg-pedra-800">
                            <div className="absolute h-2 rounded-full bg-ouro" style={{ left: `${Math.min(100, (f[0] / Math.max(1, z.nivelMax)) * 100)}%`, width: `${Math.max(2, Math.min(100, ((f[1] - f[0]) / Math.max(1, z.nivelMax)) * 100))}%` }} />
                          </div>
                        </td>
                        <td className="text-right"><button type="button" title="Tirar da lista (volta ao padrão do mod)" onClick={() => { const r = { ...z.dimensoes }; delete r[id]; set("zonas", { dimensoes: r }); }} className="text-xs text-red-400 hover:underline">remover</button></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table></div>
              <form className="mt-3 flex flex-wrap items-center gap-2 text-sm" onSubmit={(e) => {
                e.preventDefault();
                const campo = e.currentTarget.elements.namedItem("dim") as HTMLInputElement;
                const id = campo.value.trim().toLowerCase();
                if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id) || z.dimensoes[id]) return;
                set("zonas", { dimensoes: { ...z.dimensoes, [id]: [20, 40] } });
                campo.value = "";
              }}>
                <input name="dim" placeholder="mod:dimensao (ex.: alexscaves:... )" className="w-64 max-w-full rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 font-mono text-xs outline-none focus:border-ouro" />
                <button className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">+ Adicionar dimensão</button>
                <span className="text-xs text-pedra-500">Dimensão fora da lista usa 20–40.</span>
              </form>
            </Cartao>
            <Cartao titulo="Como um zumbi fica pelo mapa" ajuda="Zumbi normal: 20 de vida e 3 de dano.">
              <div className="overflow-x-auto"><table className="w-full text-sm">
                <thead className="text-left text-xs text-pedra-500"><tr><th>Distância do spawn</th><th>Nível</th><th className="text-right">Vida</th><th className="text-right">Dano</th><th className="text-right">Armadura</th></tr></thead>
                <tbody>
                  {[0, 250, 500, 1000, 2000, 3500, 5000, 8000].map((dist) => {
                    const n = Math.max(1, Math.min(z.nivelMax, 1 + Math.floor(dist / Math.max(1, z.blocosPorNivel))));
                    const cor = n >= 40 ? "text-red-500" : n >= 25 ? "text-red-300" : n >= 10 ? "text-yellow-300" : "text-green-400";
                    return (
                      <tr key={dist} className="border-t border-pedra-800">
                        <td className="py-1.5 font-mono">{fmt(dist)} blocos</td>
                        <td className={`font-titulo ${cor}`}>Nv {n}</td>
                        <td className="text-right font-mono">{fmt(20 * (1 + ((n - 1) * z.vidaPct) / 100))}</td>
                        <td className="text-right font-mono">{(3 * (1 + ((n - 1) * z.danoPct) / 100)).toFixed(1)}</td>
                        <td className="text-right font-mono">{Math.min(20, (n - 1) * z.armaduraPorNivel).toFixed(1)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table></div>
            </Cartao>
          </div>
        )}

        {/* ---------------- invasões */}
        {secao === "invasoes" && (
          <div className="space-y-4">
            <Chave rotulo="Invasões ativas" ajuda="Desligue para nenhuma horda atacar as capitais. Teste no jogo com /frx invasao tempest." valor={inv.ativo} onChange={(v) => set("invasoes", { ativo: v })} />
            <div className={`rounded-xl border border-ouro/30 bg-ouro/5 p-3 text-sm ${inv.ativo ? "" : "opacity-50"}`}>
              🗓️ A cada <b>{inv.intervaloMinutos} min</b>, se tiver alguém numa capital, há <b>{inv.chance}%</b> de chance de uma horda atacar em <b>{inv.ondas} ondas</b>.
              {" "}Quem defende ganha <b>{fmt(inv.moedasPorJogador)} cobre</b> + {inv.moedasPorAbate} por abate e <b>{inv.repVitoria}</b> de reputação.
            </div>
            <div className="grid gap-4 xl:grid-cols-3">
              <Cartao titulo="Frequência">
                <div className="space-y-4">
                  <Numero rotulo="Sortear a cada" valor={inv.intervaloMinutos} onChange={(v) => set("invasoes", { intervaloMinutos: v })} min={5} max={240} unidade="min" />
                  <Numero rotulo="Chance em cada sorteio" valor={inv.chance} onChange={(v) => set("invasoes", { chance: v })} min={0} max={100} unidade="%" />
                  <Numero rotulo="Ondas" valor={inv.ondas} onChange={(v) => set("invasoes", { ondas: v })} min={1} max={10} />
                </div>
              </Cartao>
              <Cartao titulo="Tamanho da horda">
                <div className="space-y-4">
                  <Numero rotulo="Monstros por onda (base)" valor={inv.monstrosBase} onChange={(v) => set("invasoes", { monstrosBase: v })} min={1} max={40} />
                  <Numero rotulo="+ por jogador defendendo" valor={inv.monstrosPorJogador} onChange={(v) => set("invasoes", { monstrosPorJogador: v })} min={0} max={20} />
                  <Numero rotulo="Simular com jogadores" valor={jogadoresTeste} onChange={setJogadoresTeste} min={1} max={10} />
                  <p className="text-sm">≈ <b>{inv.monstrosBase + inv.monstrosPorJogador * jogadoresTeste}</b> monstros por onda · <b>{(inv.monstrosBase + inv.monstrosPorJogador * jogadoresTeste) * inv.ondas}</b> no total</p>
                </div>
              </Cartao>
              <Cartao titulo="Recompensa">
                <div className="space-y-4">
                  <Numero rotulo="Moedas por defensor" valor={inv.moedasPorJogador} onChange={(v) => set("invasoes", { moedasPorJogador: v })} min={0} max={5000} passo={10} unidade="cobre" />
                  <Numero rotulo="+ moedas por abate" valor={inv.moedasPorAbate} onChange={(v) => set("invasoes", { moedasPorAbate: v })} min={0} max={200} unidade="cobre" />
                  <Numero rotulo="Reputação pela vitória" valor={inv.repVitoria} onChange={(v) => set("invasoes", { repVitoria: v })} min={0} max={2000} passo={10} />
                  <Numero rotulo="+ reputação por abate" valor={inv.repPorAbate} onChange={(v) => set("invasoes", { repPorAbate: v })} min={0} max={100} />
                </div>
              </Cartao>
            </div>
            <Cartao titulo="Monstros de cada onda" ajuda="Clique em “Escolher na lista” para marcar os monstros pela imagem e pelo nome.">
              <div className="space-y-4">
                <Etiquetas rotulo="Onda 1" valores={inv.monstros1} onChange={(v) => set("invasoes", { monstros1: v })} exemplo="minecraft:zombie" />
                <Etiquetas rotulo="Onda 2" valores={inv.monstros2} onChange={(v) => set("invasoes", { monstros2: v })} exemplo="minecraft:pillager" />
                <Etiquetas rotulo="Onda 3 em diante" valores={inv.monstros3} onChange={(v) => set("invasoes", { monstros3: v })} exemplo="minecraft:evoker" />
                <UmaCriatura rotulo="Comandante da última onda (vida ×3)" valor={inv.chefe} onChange={(v) => set("invasoes", { chefe: v })} />
              </div>
            </Cartao>
          </div>
        )}

        {/* ---------------- chefe mundial */}
        {secao === "chefe" && (
          <div className="space-y-4">
            <Chave rotulo="Chefe mundial ativo" ajuda="Nos horários marcados um chefe fortíssimo surge longe das capitais; todos recebem aviso antes. Teste com /frx chefe agora." valor={d.chefeMundial.ativo} onChange={(v) => set("chefeMundial", { ativo: v })} />
            <div className="grid gap-4 xl:grid-cols-2">
              <Cartao titulo="Agenda" ajuda="Horário de Brasília.">
                <div className="flex flex-wrap gap-2">
                  {d.chefeMundial.horarios.map((h) => {
                    const [dia, hora] = h.split(" ");
                    return (
                      <span key={h} className="flex items-center gap-2 rounded-xl border border-red-500/40 bg-red-950/20 px-3 py-1.5 text-sm">
                        📅 {NOME_DIA[dia] ?? dia} <b className="font-mono">{hora}</b>
                        <button onClick={() => set("chefeMundial", { horarios: d.chefeMundial.horarios.filter((x) => x !== h) })} className="text-pedra-500 hover:text-red-300">×</button>
                      </span>
                    );
                  })}
                  {!d.chefeMundial.horarios.length && <p className="text-sm text-pedra-500">Nenhum horário: só surge com /frx chefe agora.</p>}
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <select value={novoDia} onChange={(e) => setNovoDia(e.target.value)} className="rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm">
                    {DIAS.map((x) => <option key={x} value={x}>{NOME_DIA[x]}</option>)}
                  </select>
                  <input type="time" value={novaHora} onChange={(e) => setNovaHora(e.target.value)} className="rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm" />
                  <button onClick={() => { const h = `${novoDia} ${novaHora}`; if (!d.chefeMundial.horarios.includes(h)) set("chefeMundial", { horarios: [...d.chefeMundial.horarios, h] }); }}
                    className="rounded-md border border-ouro/50 px-3 py-1 text-sm text-ouro hover:bg-ouro/15">+ adicionar horário</button>
                </div>
                {prox && d.chefeMundial.ativo && <p className="mt-3 text-sm">⏰ Próximo: <b>{prox.toLocaleString("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</b> (aviso {d.chefeMundial.avisoMinutos} min antes)</p>}
              </Cartao>
              <Cartao titulo="Força e recompensa">
                <div className="space-y-4">
                  <Numero rotulo="Vida" valor={d.chefeMundial.vidaMult} onChange={(v) => set("chefeMundial", { vidaMult: v })} min={1} max={50} passo={0.5} unidade="×" />
                  <Numero rotulo="Dano" valor={d.chefeMundial.danoMult} onChange={(v) => set("chefeMundial", { danoMult: v })} min={1} max={10} passo={0.1} unidade="×" />
                  <Numero rotulo="Pote de moedas (repartido pelo dano)" valor={d.chefeMundial.moedas} onChange={(v) => set("chefeMundial", { moedas: v })} min={0} max={100000} passo={100} unidade="cobre" />
                </div>
              </Cartao>
              <Cartao titulo="Tempo e lugar">
                <div className="space-y-4">
                  <Numero rotulo="Aviso antes de surgir" valor={d.chefeMundial.avisoMinutos} onChange={(v) => set("chefeMundial", { avisoMinutos: v })} min={1} max={60} unidade="min" />
                  <Numero rotulo="Tempo para matar (depois ele foge)" valor={d.chefeMundial.duracaoMinutos} onChange={(v) => set("chefeMundial", { duracaoMinutos: v })} min={5} max={240} unidade="min" />
                  <Numero rotulo="Distância mínima do spawn" valor={d.chefeMundial.distanciaMin} onChange={(v) => set("chefeMundial", { distanciaMin: v })} min={100} max={10000} passo={50} unidade="blocos" />
                  <Numero rotulo="Distância máxima do spawn" valor={d.chefeMundial.distanciaMax} onChange={(v) => set("chefeMundial", { distanciaMax: v })} min={200} max={20000} passo={50} unidade="blocos" />
                </div>
              </Cartao>
              <Cartao titulo="Chefes sorteados" ajuda="Um destes é sorteado a cada evento.">
                <Etiquetas rotulo="Chefes" valores={d.chefeMundial.chefes} onChange={(v) => set("chefeMundial", { chefes: v })} exemplo="cataclysm:ignis" filtro="chefes" />
              </Cartao>
            </div>
          </div>
        )}

        {/* ---------------- floresta */}
        {secao === "floresta" && (
          <div className="space-y-4">
            <Chave rotulo="Floresta sombria ativa" ajuda="Monstros sombrios nascem perto de quem passa pela mata." valor={fl.ativo} onChange={(v) => set("floresta", { ativo: v })} />
            <div className="grid gap-4 xl:grid-cols-[1fr_340px]">
              <div className="space-y-4">
                <Cartao titulo="Onde fica" ajuda="Início e largura só valem para chunks que ainda não foram gerados.">
                  <div className="space-y-4">
                    <label className="block text-sm">Capital
                      <select value={fl.reino} onChange={(e) => set("floresta", { reino: e.target.value })} className="mt-1 block rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm">
                        {["tempest", "dwargon", "sarion", "leste", "leste-sakura", "oeste", "lubelius", "demonios"].map((r) => <option key={r} value={r}>{r}</option>)}
                      </select>
                    </label>
                    <Numero rotulo="Começa a" valor={fl.inicio} onChange={(v) => set("floresta", { inicio: v })} min={10} max={500} unidade="blocos" ajuda="Distância da borda da capital até a mata." />
                    <Numero rotulo="Largura da mata" valor={fl.largura} onChange={(v) => set("floresta", { largura: v })} min={30} max={500} unidade="blocos" />
                  </div>
                </Cartao>
                <Cartao titulo="Perigo">
                  <div className="space-y-4">
                    <Numero rotulo="Máximo de monstros por jogador" valor={fl.maxMonstros} onChange={(v) => set("floresta", { maxMonstros: v })} min={0} max={40} />
                    <Numero rotulo="Novos monstros a cada" valor={fl.intervaloSeg} onChange={(v) => set("floresta", { intervaloSeg: v })} min={2} max={60} unidade="s" />
                    <Numero rotulo="Chance de monstro forte" valor={fl.chanceForte} onChange={(v) => set("floresta", { chanceForte: v })} min={0} max={100} unidade="%" ajuda="Só do primeiro terço da mata para dentro." />
                    <Chave rotulo="Escuridão" ajuda="Efeito de escuridão dentro da mata." valor={fl.escuridao} onChange={(v) => set("floresta", { escuridao: v })} />
                    <Chave rotulo="Só a pé" ajuda="Corta élitro e montaria." valor={fl.soAPe} onChange={(v) => set("floresta", { soAPe: v })} />
                  </div>
                </Cartao>
                <Cartao titulo="Monstros">
                  <div className="space-y-4">
                    <Etiquetas rotulo="Comuns" valores={fl.monstros1} onChange={(v) => set("floresta", { monstros1: v })} exemplo="graveyard:ghoul" />
                    <Etiquetas rotulo="Fortes" valores={fl.monstros2} onChange={(v) => set("floresta", { monstros2: v })} exemplo="graveyard:wraith" />
                  </div>
                </Cartao>
              </div>
              <Cartao titulo="Vista de cima" ajuda="Capital no meio, a mata em volta e as 4 trilhas.">
                {(() => {
                  const cap = 160, total = cap + 2 * (fl.inicio + fl.largura);
                  const k = 300 / total, c = 150;
                  const r1 = (cap / 2 + fl.inicio) * k, r2 = (cap / 2 + fl.inicio + fl.largura) * k;
                  return (
                    <svg viewBox="0 0 300 300" className="w-full rounded-lg bg-[#5d9c3a]/40">
                      <rect x={c - r2} y={c - r2} width={2 * r2} height={2 * r2} fill="#1d3b1a" rx="10" />
                      <rect x={c - r1} y={c - r1} width={2 * r1} height={2 * r1} fill="#6aa84f" />
                      {Array.from({ length: 60 }).map((_, i) => {
                        const a = (i * 137.5) % 360, rr = r1 + ((i * 53) % 100) / 100 * (r2 - r1);
                        const x = c + Math.cos((a * Math.PI) / 180) * rr, y = c + Math.sin((a * Math.PI) / 180) * rr;
                        return Math.abs(x - c) > r1 || Math.abs(y - c) > r1 ? <circle key={i} cx={x} cy={y} r="4" fill="#0f2a10" /> : null;
                      })}
                      <rect x={c - 3} y={c - r2} width="6" height={2 * r2} fill="#8b6b3d" />
                      <rect x={c - r2} y={c - 3} width={2 * r2} height="6" fill="#8b6b3d" />
                      <rect x={c - (cap / 2) * k} y={c - (cap / 2) * k} width={cap * k} height={cap * k} fill="#d4a93a" stroke="#000" strokeOpacity=".4" />
                      <text x={c} y={c + 4} textAnchor="middle" fontSize="11" fill="#1a1208">{fl.reino}</text>
                    </svg>
                  );
                })()}
              </Cartao>
            </div>
          </div>
        )}
      </div>

      {/* barra de salvar */}
      <div className={`fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 items-center gap-3 rounded-2xl border px-4 py-2.5 shadow-2xl transition ${mudou ? "border-ouro/60 bg-pedra-900" : "border-pedra-700 bg-pedra-900/90"}`}>
        {mudou ? <span className="text-sm text-yellow-200">● Alterações não salvas em {SECOES.filter(secaoMudou).map((s) => s.nome).join(", ")}</span>
          : msg ? <span className={`text-sm ${msg.ok ? "text-green-300" : "text-red-300"}`}>{msg.texto}</span>
          : <span className="text-sm text-pedra-500">Tudo salvo.</span>}
        {mudou && <button onClick={() => { setD(salvo); setMsg(null); }} className="rounded-md border border-pedra-700 px-3 py-1 text-sm hover:border-ouro">Descartar</button>}
        <button disabled={!mudou || pendente} onClick={salvar} className="rounded-md bg-ouro px-5 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-40">{pendente ? "Salvando…" : "Salvar"}</button>
      </div>
      {mudou && msg && !msg.ok && <div className="fixed bottom-20 left-1/2 z-40 -translate-x-1/2 rounded-lg bg-red-950/95 px-4 py-2 text-sm text-red-200">{msg.texto}</div>}
    </div>
  );
}
