"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Inventario, type Item } from "./inventario";
import { RostoSkin } from "./boneco";
import { IconeCriatura, useCriaturas } from "@/components/criaturas";

// ------------------------------------------------------------------ formato da ficha (vem do mod)

type Talento = { id: string; nome: string; atual: number; max: number; liberado: boolean; porPonto: string; efeito: string; pede: string };
type Ficha = {
  nome: string; uuid: string; online: boolean; nivel: number; nivelMax: number; rank: string; xp: number; xpProximo: number;
  classe: string; classeId?: string; raca?: { principal: string; secundaria: string; nivel: string; descricao?: string; bonus: string[] }; unica?: { nome: string; descricao: string; nivel: number; efeito: string; abates: number }; recurso?: string; recursoAtual?: number; recursoMax?: number;
  moedas: string; banco: string; moedasCobre?: number; bancoCobre?: number; bancoRende?: string; titulo: string; titulos: string[]; zona: number;
  x: number; y: number; z: number; dim: string; modo: string; fome: number; nivelVanilla: number;
  tempoJogoMin?: number; mobsMortos?: number; mortesVanilla?: number; blocosAndados?: number;
  atributos: Record<string, number>;
  estatisticas: Record<string, number>;
  talentos: { livres: number; total: number; ramos: { nome: string; pontos: number; talentos: Talento[] }[] };
  reinos: { id: string; nome: string; pontos: number; nivel: string; indice: number; desconto: number; minimo: number; proximo: number }[];
  campanhas: { reino: string; nome: string; rei: string; nivelMin: number; etapa: number; total: number; etapaTitulo?: string; pedido?: string; progresso?: number; qtd?: number }[];
  conquistas: { nome: string; descricao: string; titulo: string; obtida: boolean; progresso: number; alvo: number }[];
  grupo?: { membros?: { nome: string }[] } | Record<string, unknown>;
  habilidades?: { codigo: string; nome: string; descricao: string; nivelReq: number; ultimate: boolean; porItem: boolean; aprendida: boolean; nivel?: number; usos?: number }[];
  barra?: string[];
  sombras?: { id: string; tipo: string; nome: string; abates: number; nivel: number; chefe: boolean; ativa: boolean }[];
  efeitos?: { nome: string; nivel: number; seg: number }[];
  inventario?: Item[]; armadura?: Item[]; maoSecundaria?: Item; slotSelecionado?: number; bauEnder?: Item[];
};

export type JogadorBase = {
  uuid: string; nick: string; nivel: number; rank: string; classe: string; titulo: string; conquistas: number;
  mortes: number; moedas: number; banco: number; ativo: boolean; criadoEm: number; ultimoLogin: number | null;
};

const CLASSES = ["guerreiro", "arqueiro", "mago", "clerigo", "ladino", "assassino", "invocador"];
const ICONE_CLASSE: Record<string, string> = { Guerreiro: "⚔️", Arqueiro: "🏹", Mago: "🔮", "Clérigo": "✨", Ladino: "🎭", Assassino: "🗡️", Invocador: "👻", Paladino: "🛡️", Bardo: "🎵", Monge: "👊", Druida: "🌿" };
const ICONE_ATR: Record<string, [string, string, string?]> = {
  vida: ["❤️", "Vida"], vidaMax: ["💗", "Vida máxima"], ataque: ["⚔️", "Ataque"], velAtaque: ["💨", "Vel. de ataque"],
  armadura: ["🛡️", "Armadura"], resistencia: ["🪨", "Resistência"], velocidade: ["👟", "Velocidade", "%"], sorte: ["🍀", "Sorte"],
  critico: ["💥", "Crítico", "%"], esquiva: ["🌀", "Esquiva", "%"], roubo: ["🩸", "Roubo de vida", "%"],
};
const NOME_EST: Record<string, string> = {
  matar_total: "Monstros abatidos (FRX)", mortes: "Mortes", chefes: "Chefes derrotados", invasoes: "Invasões vencidas",
  campanhas: "Campanhas concluídas", forja_max: "Maior forja", conquistas: "Conquistas",
};

const fmt = (n: number | undefined) => (n ?? 0).toLocaleString("pt-BR");
const quando = (t: number | null) => (t ? new Date(t).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) : "—");

function Cartao({ titulo, children, className = "" }: { titulo: string; children: React.ReactNode; className?: string }) {
  return (
    <section className={`rounded-xl border border-pedra-700 bg-pedra-900/80 p-4 ${className}`}>
      <h3 className="mb-3 font-titulo text-lg text-ouro">{titulo}</h3>
      {children}
    </section>
  );
}

function Progresso({ v, max, cor = "bg-ouro" }: { v: number; max: number; cor?: string }) {
  return (
    <div className="h-2 overflow-hidden rounded-full bg-pedra-800">
      <div className={`h-full rounded-full ${cor} transition-all duration-700`} style={{ width: `${max > 0 ? Math.min(100, (v / max) * 100) : 0}%` }} />
    </div>
  );
}

// ------------------------------------------------------------------ tela

export function FichaJogador({ base, inicial, inicialEm, inicialOnline, inicialOp, congelado, leitura }: { base: JogadorBase; inicial: Ficha | null; inicialEm: number | null; inicialOnline: boolean; inicialOp: boolean; /** nome do personagem fora de uso: a ficha é um retrato, sem atualizar nem agir */ congelado?: string; /** ficha do próprio jogador no site: só consulta, sem nenhum botão de admin */ leitura?: boolean }) {
  const [op, setOp] = useState(inicialOp);
  const [ficha, setFicha] = useState<Ficha | null>(inicial);
  const [em, setEm] = useState(inicialEm);
  const [online, setOnline] = useState(inicialOnline);
  const [aba, setAba] = useState("status");
  const [aviso, setAviso] = useState<{ ok: boolean; texto: string } | null>(null);
  const [enviando, setEnviando] = useState(false);

  const recarregar = useCallback(async () => {
    if (congelado) return;
    const r = await fetch(`/api/painel/jogadores/${base.uuid}`, { cache: "no-store" }).catch(() => null);
    if (!r?.ok) return;
    const j = await r.json();
    if (j.detalhe) setFicha(j.detalhe);
    setEm(j.detalheEm);
    setOnline(j.online);
  }, [base.uuid, congelado]);

  useEffect(() => {
    const t = setInterval(recarregar, 15_000);
    return () => clearInterval(t);
  }, [recarregar]);

  const acao = useCallback(async (nome: string, valor?: string | number, alvo?: string, texto?: string) => {
    if (congelado) { setAviso({ ok: false, texto: `${congelado} não está em uso. As ações só valem para o personagem em uso da conta.` }); return; }
    setEnviando(true);
    const r = await fetch(`/api/painel/jogadores/${base.uuid}/acao`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ acao: nome, valor, alvo }),
    }).catch(() => null);
    const j = r ? await r.json().catch(() => ({})) : { erro: "Sem resposta do painel." };
    setEnviando(false);
    if (!r?.ok) { setAviso({ ok: false, texto: j.erro ?? "Falhou." }); return; }
    setAviso({ ok: true, texto: texto ?? `Feito: /${j.comando}` });
    if (nome === "op") setOp(true);
    if (nome === "deop") setOp(false);
    setTimeout(recarregar, 1500);
    setTimeout(recarregar, 4000);
  }, [base.uuid, recarregar, congelado]);

  useEffect(() => { if (!aviso) return; const t = setTimeout(() => setAviso(null), 5000); return () => clearTimeout(t); }, [aviso]);

  const f = ficha;
  const abas: [string, string][] = [
    ["status", "📊 Status"], ["habilidades", "✨ Habilidades"], ["talentos", "🌳 Talentos"], ["reinos", "👑 Reinos"],
    ["conquistas", `🏆 Conquistas${f ? ` (${f.conquistas.filter((c) => c.obtida).length}/${f.conquistas.length})` : ""}`], ["admin", "🛠️ Admin"],
  ];

  return (
    <div className="space-y-5">
      {!leitura && <Link href="/admin/jogadores" className="text-sm text-pedra-500 hover:text-ouro">← Todos os jogadores</Link>}

      {/* ---------------- cabeçalho */}
      <div className="relative overflow-hidden rounded-2xl border border-ouro/30 bg-gradient-to-r from-pedra-900 via-pedra-800 to-pedra-900 p-5">
        <div className="flex flex-wrap items-center gap-5">
          <span className="shrink-0 overflow-hidden rounded-lg ring-2 ring-ouro/40"><RostoSkin uuid={base.uuid} tam={80} /></span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-titulo text-3xl">{base.nick}</h1>
              <span className={`rounded-full px-2.5 py-0.5 text-xs ${online ? "bg-green-500/20 text-green-300" : "bg-pedra-700 text-pedra-500"}`}>
                {online ? "● online" : "○ offline"}
              </span>
              {!base.ativo && <span className="rounded-full bg-red-500/20 px-2.5 py-0.5 text-xs text-red-300">acesso revogado</span>}
              {op && <span className="rounded-full bg-ouro/20 px-2.5 py-0.5 text-xs text-ouro">👑 admin do servidor</span>}
              <button style={leitura ? { display: "none" } : undefined} disabled={enviando}
                onClick={() => { if (confirm(op ? `Tirar o admin de ${base.nick}?` : `Tornar ${base.nick} admin do servidor? Ele vai poder usar todos os comandos (/gamemode, /give, /tp, /frx...).`)) acao(op ? "deop" : "op", undefined, undefined, op ? `${base.nick} não é mais admin.` : `${base.nick} agora é admin.`); }}
                className={`rounded border px-2 py-0.5 text-xs disabled:opacity-40 ${op ? "border-red-500/50 text-red-300 hover:bg-red-500/15" : "border-ouro/50 text-ouro hover:bg-ouro/15"}`}>
                {op ? "Remover admin" : "Tornar admin"}
              </button>
            </div>
            {(f?.titulo || base.titulo) && <p className="text-purple-300">«{f?.titulo || base.titulo}»</p>}
            <p className="mt-1 text-sm text-pedra-500">
              {ICONE_CLASSE[f?.classe ?? ""] ?? "❔"} {f?.classe || "sem classe"}{f?.raca ? ` · 🧬 ${f.raca.principal}${f.raca.secundaria ? ` / ${f.raca.secundaria}` : ""}` : ""} · Rank <span className="font-titulo text-ouro">{f?.rank ?? base.rank}</span>
              {" "}· entrou em {quando(base.criadoEm)} · último login {quando(base.ultimoLogin)}
            </p>
          </div>
          <div className="w-64">
            <div className="flex items-baseline justify-between">
              <span className="font-titulo text-4xl text-ouro">Nv {f?.nivel ?? base.nivel}</span>
              {f && <span className="text-xs text-pedra-500">{fmt(f.xp)} / {fmt(f.xpProximo)} XP</span>}
            </div>
            {f && <Progresso v={f.xp} max={f.xpProximo} cor="bg-green-500" />}
            {f?.recurso && <div className="mt-2"><p className="text-xs text-pedra-500">{f.recurso} {f.recursoAtual}/{f.recursoMax}</p>
              <Progresso v={f.recursoAtual ?? 0} max={f.recursoMax ?? 1} cor={f.recurso === "Fúria" ? "bg-red-500" : f.recurso === "Mana" ? "bg-blue-500" : "bg-yellow-400"} /></div>}
          </div>
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-1 text-sm">
          <span>💰 Bolsa: <b className="text-ouro">{f?.moedas ?? `${base.moedas} cobre`}</b></span>
          <span>🏦 Banco: <b className="text-ouro">{f?.banco ?? `${base.banco} cobre`}</b></span>
          {f?.bancoRende && <span className="text-xs text-green-400">📈 {f.bancoRende}</span>}
          <span>🏆 {base.conquistas} conquistas</span>
          <span>💀 {base.mortes} mortes</span>
          {f && <span>📍 {f.x}, {f.y}, {f.z} <span className="text-pedra-500">({f.dim.replace("minecraft:", "")} · zona Nv {f.zona})</span></span>}
          <span className="ml-auto text-xs text-pedra-500">
            ficha de {quando(em)}{" "}
            <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando} onClick={() => acao("ficha", undefined, undefined, "Pedindo ficha nova ao servidor…")} className="ml-2 rounded border border-pedra-700 px-2 py-0.5 hover:border-ouro disabled:opacity-40">↻ atualizar</button>
          </span>
        </div>
      </div>

      {aviso && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg border px-4 py-3 text-sm shadow-xl ${aviso.ok ? "border-green-500/50 bg-green-950/90 text-green-200" : "border-red-500/50 bg-red-950/90 text-red-200"}`}>{aviso.texto}</div>
      )}

      {!f ? (
        <Cartao titulo="Sem ficha ainda">
          <p className="text-sm text-pedra-500">A ficha completa (inventário, habilidades, conquistas) chega quando o jogador entrar no servidor com o core mod 0.30 ou mais novo.</p>
        </Cartao>
      ) : (
        <div className={`grid gap-5 ${leitura ? "" : "2xl:grid-cols-[auto_1fr]"}`}>{/* na página da conta (mais estreita) o inventário fica em cima e as abas usam a largura toda */}
          {/* ---------------- inventário com bonequinho */}
          <div className="space-y-3">
            <Inventario uuid={base.uuid} nick={base.nick} inventario={f.inventario ?? []} armadura={f.armadura ?? []}
              maoSecundaria={f.maoSecundaria} selecionado={f.slotSelecionado ?? 0} ender={f.bauEnder ?? []} />
            <div className="flex flex-wrap gap-2 text-xs text-pedra-500">
              <span>🎮 {f.modo}</span><span>🍗 fome {f.fome}/20</span><span>✳️ XP vanilla {f.nivelVanilla}</span>
              {f.tempoJogoMin !== undefined && <span>⏱️ {Math.floor(f.tempoJogoMin / 60)}h{String(f.tempoJogoMin % 60).padStart(2, "0")} jogadas</span>}
            </div>
            {!!f.efeitos?.length && (
              <div className="flex flex-wrap gap-1.5">
                {f.efeitos.map((e) => <span key={e.nome} className="rounded-full border border-purple-500/40 bg-purple-950/40 px-2 py-0.5 text-xs text-purple-200">🧪 {e.nome} {e.nivel > 1 ? e.nivel : ""} · {Math.floor(e.seg / 60)}:{String(e.seg % 60).padStart(2, "0")}</span>)}
              </div>
            )}
          </div>

          {/* ---------------- abas */}
          <div className="min-w-0">
            <div className="mb-3 flex flex-wrap gap-1.5">
              {abas.filter(([id]) => !leitura || id !== "admin").map(([id, n]) => (
                <button key={id} onClick={() => setAba(id)}
                  className={`rounded-lg border px-3 py-1.5 text-sm transition ${aba === id ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:border-ouro/50 hover:text-pergaminho"}`}>{n}</button>
              ))}
            </div>

            {aba === "status" && (
              <div className="grid gap-4 lg:grid-cols-2">
                <Cartao titulo="Raça" className="lg:col-span-2">
                  {f.raca ? (
                    <div className="flex flex-wrap items-start gap-x-8 gap-y-3">
                      <div>
                        <p className="font-titulo text-xl text-ouro">🧬 {f.raca.principal}{f.raca.secundaria ? <span className="text-pergaminho"> / {f.raca.secundaria}</span> : null}</p>
                        <p className="text-xs text-pedra-500">{f.raca.secundaria ? "Raça principal / secundária" : "Raça pura"} · {f.raca.nivel}</p>
                        {f.raca.descricao && <p className="mt-1 max-w-md text-sm text-pedra-500">{f.raca.descricao}</p>}
                      </div>
                      <ul className="flex flex-1 flex-wrap gap-1.5">
                        {f.raca.bonus.map((b) => <li key={b} className={`rounded-full border px-2.5 py-0.5 text-xs ${/^[−-]/.test(b) ? "border-red-500/40 text-red-300" : "border-green-500/40 text-green-300"}`}>{b}</li>)}
                      </ul>
                    </div>
                  ) : <p className="text-sm text-pedra-500">Ainda não escolheu a raça (no jogo: <code>/raca</code>).</p>}
                </Cartao>
                <Cartao titulo="Habilidade única" className="lg:col-span-2">
                  {f.unica ? (
                    <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
                      <p className="font-titulo text-xl text-purple-300">✦ {f.unica.nome} <span className="text-sm text-pedra-500">Nv {f.unica.nivel}/5</span></p>
                      <p className="text-sm">{f.unica.descricao}</p>
                      <span className="rounded-full border border-purple-500/40 px-2.5 py-0.5 text-xs text-purple-200">{f.unica.efeito}</span>
                      <span className="text-xs text-pedra-500">{fmt(f.unica.abates)} monstros abatidos com ela</span>
                    </div>
                  ) : <p className="text-sm text-pedra-500">Desperta sozinha no próximo login do jogador.</p>}
                </Cartao>
                <Cartao titulo="Atributos">
                  <ul className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-2">
                    {Object.entries(f.atributos).map(([k, v]) => {
                      const [ic, nome, suf] = ICONE_ATR[k] ?? ["•", k];
                      return <li key={k} className="flex items-center justify-between gap-2 rounded-lg bg-pedra-950/60 px-3 py-2 text-sm"><span className="min-w-0 truncate">{ic} {nome}</span><b className="shrink-0 font-mono text-pergaminho">{v}{suf ?? ""}</b></li>;
                    })}
                  </ul>
                </Cartao>
                <Cartao titulo="Estatísticas">
                  <ul className="space-y-1.5 text-sm">
                    {Object.entries(f.estatisticas).map(([k, v]) => <li key={k} className="flex justify-between border-b border-pedra-800 pb-1"><span className="text-pedra-500">{NOME_EST[k] ?? k}</span><b>{fmt(v)}</b></li>)}
                    {f.mobsMortos !== undefined && <li className="flex justify-between border-b border-pedra-800 pb-1"><span className="text-pedra-500">Criaturas mortas (total)</span><b>{fmt(f.mobsMortos)}</b></li>}
                    {f.blocosAndados !== undefined && <li className="flex justify-between border-b border-pedra-800 pb-1"><span className="text-pedra-500">Blocos andados</span><b>{fmt(f.blocosAndados)}</b></li>}
                  </ul>
                </Cartao>
                <Cartao titulo="Títulos liberados" className="lg:col-span-2">
                  {f.titulos.length ? <div className="flex flex-wrap gap-2">{f.titulos.map((t) => <span key={t} className={`rounded-full border px-3 py-1 text-sm ${t === f.titulo ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700"}`}>«{t}»</span>)}</div>
                    : <p className="text-sm text-pedra-500">Nenhum ainda.</p>}
                </Cartao>
              </div>
            )}

            {aba === "habilidades" && (
              <Cartao titulo={`Habilidades · ${f.classe || "sem classe"}`}>
                {!f.habilidades?.length ? <p className="text-sm text-pedra-500">Sem classe ou sem habilidades cadastradas.</p> : (
                  <>
                    <div className="mb-4 flex flex-wrap items-center gap-2">
                      <span className="text-xs uppercase tracking-wider text-pedra-500">Barra:</span>
                      {(f.barra ?? []).map((c, i) => {
                        const h = f.habilidades!.find((x) => x.codigo === c);
                        return <span key={i} className={`rounded-md border px-2 py-1 text-xs ${i === 4 ? "border-ouro/60" : "border-pedra-700"}`}>{i === 4 ? "★" : i + 1}: {h?.nome ?? "—"}</span>;
                      })}
                      <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando} onClick={() => acao("habTodas")} className="ml-auto rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro disabled:opacity-40">Ensinar todas</button>
                    </div>
                    <ul className="grid gap-2 md:grid-cols-2">
                      {f.habilidades.map((h) => (
                        <li key={h.codigo} className={`rounded-lg border p-3 ${h.aprendida ? "border-purple-500/40 bg-purple-950/20" : "border-pedra-700 bg-pedra-950/40 opacity-80"}`}>
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <p className="font-semibold">{h.ultimate && "★ "}{h.nome} {h.aprendida && <span className="text-xs text-purple-300">Nv {h.nivel}</span>}</p>
                              <p className="text-xs text-pedra-500">{h.descricao}</p>
                              <p className="mt-1 text-xs text-pedra-500">Pede nível {h.nivelReq}{h.porItem ? " · por tomo" : ""}{h.aprendida ? ` · usada ${fmt(h.usos)}×` : ""}</p>
                            </div>
                            <div className="flex shrink-0 flex-col items-end gap-1">
                              {h.aprendida ? (
                                <>
                                  <select disabled={leitura || !online || enviando} value={h.nivel} onChange={(e) => acao("habNivel", e.target.value, h.codigo)}
                                    className="rounded border border-pedra-700 bg-pedra-950 px-1 py-0.5 text-xs">
                                    {Array.from({ length: 30 }, (_, i) => <option key={i + 1} value={i + 1}>Nv {i + 1}</option>)}
                                  </select>
                                  <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando} onClick={() => acao("habEsquecer", undefined, h.codigo)} className="text-xs text-red-400 hover:underline disabled:opacity-40">esquecer</button>
                                </>
                              ) : (
                                <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando} onClick={() => acao("habAprender", undefined, h.codigo)} className="rounded border border-purple-500/50 px-2 py-0.5 text-xs text-purple-200 hover:bg-purple-500/20 disabled:opacity-40">ensinar</button>
                              )}
                            </div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </Cartao>
            )}

            {aba === "habilidades" && (f.classeId === "INVOCADOR" || !!f.sombras?.length) && <ExercitoSombras f={f} />}

            {aba === "talentos" && (
              <Cartao titulo={`Talentos · ${f.talentos.livres} livres de ${f.talentos.total}`}>
                <div className="mb-3 flex justify-end">
                  <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando} onClick={() => acao("talentosResetar")} className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro disabled:opacity-40">Resetar de graça</button>
                </div>
                {!f.talentos.ramos?.length ? <p className="text-sm text-pedra-500">Escolha uma classe para ver a árvore.</p> : (
                  <div className="grid gap-4 md:grid-cols-3">
                    {f.talentos.ramos.map((r) => (
                      <div key={r.nome} className="rounded-lg border border-pedra-700 bg-pedra-950/40 p-3">
                        <p className="mb-2 font-titulo text-ouro">{r.nome} <span className="text-xs text-pedra-500">· {r.pontos} pts</span></p>
                        <ul className="space-y-2">
                          {r.talentos.map((t) => (
                            <li key={t.id} className={`text-sm ${t.liberado ? "" : "opacity-50"}`}>
                              <div className="flex items-center justify-between gap-2">
                                <span>{t.nome}</span>
                                <span className="flex items-center gap-1">
                                  <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando || t.atual <= 0} onClick={() => acao("talento", t.atual - 1, t.id)} className="h-5 w-5 rounded border border-pedra-700 text-xs disabled:opacity-30">−</button>
                                  <b className="w-10 text-center font-mono text-xs">{t.atual}/{t.max}</b>
                                  <button style={leitura ? { display: "none" } : undefined} disabled={!online || enviando || t.atual >= t.max} onClick={() => acao("talento", t.atual + 1, t.id)} className="h-5 w-5 rounded border border-pedra-700 text-xs disabled:opacity-30">+</button>
                                </span>
                              </div>
                              <p className="text-xs text-pedra-500">{t.efeito || t.porPonto}{t.pede ? ` · ${t.pede}` : ""}</p>
                            </li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                )}
              </Cartao>
            )}

            {aba === "reinos" && (
              <div className="grid gap-4 lg:grid-cols-2">
                <Cartao titulo="Reputação">
                  <ul className="space-y-3">
                    {f.reinos.map((r) => (
                      <li key={r.id}>
                        <div className="flex items-center justify-between gap-2 text-sm">
                          <span>👑 {r.nome} <span className="text-xs text-ouro">{r.nivel}</span>{r.desconto > 0 && <span className="text-xs text-green-400"> · {r.desconto}% off</span>}</span>
                          <form style={leitura ? { display: "none" } : undefined} className="flex items-center gap-1" onSubmit={(e) => { e.preventDefault(); const v = new FormData(e.currentTarget).get("v"); acao("reputacao", String(v), r.id); }}>
                            <input name="v" type="number" defaultValue={r.pontos} className="w-20 rounded border border-pedra-700 bg-pedra-950 px-1.5 py-0.5 text-right font-mono text-xs" />
                            <button disabled={!online || enviando} className="rounded border border-pedra-700 px-1.5 py-0.5 text-xs hover:border-ouro disabled:opacity-40">✓</button>
                          </form>
                        </div>
                        <Progresso v={r.pontos - r.minimo} max={r.proximo > 0 ? r.proximo - r.minimo : 1} />
                      </li>
                    ))}
                  </ul>
                </Cartao>
                <Cartao titulo="Campanhas dos reis">
                  <ul className="space-y-3 text-sm">
                    {f.campanhas.map((c) => (
                      <li key={c.reino} className="rounded-lg bg-pedra-950/50 p-2.5">
                        <p><b>{c.nome}</b> <span className="text-xs text-pedra-500">· {c.reino} · {c.rei}</span></p>
                        <p className="text-xs text-pedra-500">
                          {c.etapa < 0 ? `Não aceita (pede nível ${c.nivelMin})` : c.etapa >= c.total ? "✅ Concluída" : `Etapa ${c.etapa + 1}/${c.total}: ${c.etapaTitulo ?? ""} ${c.qtd ? `(${c.progresso}/${c.qtd})` : ""}`}
                        </p>
                        {c.etapa >= 0 && <div className="mt-1.5"><Progresso v={Math.min(c.etapa, c.total)} max={c.total} cor="bg-purple-400" /></div>}
                      </li>
                    ))}
                  </ul>
                </Cartao>
              </div>
            )}

            {aba === "conquistas" && (
              <Cartao titulo="Conquistas">
                <ul className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                  {[...f.conquistas].sort((a, b) => Number(b.obtida) - Number(a.obtida)).map((c) => (
                    <li key={c.nome} className={`rounded-lg border p-3 ${c.obtida ? "border-ouro/50 bg-ouro/10" : "border-pedra-700 bg-pedra-950/40"}`}>
                      <p className={c.obtida ? "text-ouro" : ""}>{c.obtida ? "🏆" : "🔒"} {c.nome}</p>
                      <p className="text-xs text-pedra-500">{c.descricao}</p>
                      {c.titulo && <p className="text-xs text-purple-300">título: «{c.titulo}»</p>}
                      {!c.obtida && <div className="mt-1.5"><Progresso v={c.progresso} max={c.alvo} /><p className="mt-0.5 text-right text-xs text-pedra-500">{fmt(c.progresso)}/{fmt(c.alvo)}</p></div>}
                    </li>
                  ))}
                </ul>
              </Cartao>
            )}

            {aba === "admin" && <Admin online={online} enviando={enviando} acao={acao} f={f} />}
          </div>
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ sombras do Invocador

function ExercitoSombras({ f }: { f: Ficha }) {
  const todas = useCriaturas();
  const porId = new Map((todas ?? []).map((c) => [c.id, c]));
  const lista = f.sombras ?? [];
  const vagas = Math.min(30, 2 + Math.floor(f.nivel / 6)), juntas = Math.min(10, 1 + Math.floor(f.nivel / 10));
  return (
    <section className="mt-4 rounded-xl border border-purple-500/40 bg-gradient-to-br from-purple-950/40 to-pedra-900 p-4">
      <h3 className="font-titulo text-lg text-purple-200">⚫ Exército de sombras <span className="text-sm text-pedra-500">· {lista.length}/{vagas} · até {juntas} ao mesmo tempo</span></h3>
      <p className="mb-3 text-xs text-pedra-500">Criaturas que o Invocador ergueu com &quot;Levante-se!&quot;. Lutam com 40% a 70% da força original, crescendo conforme matam.</p>
      {!lista.length ? <p className="text-sm text-pedra-500">Nenhuma sombra ainda.</p> : (
        <ul className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {lista.map((s) => (
            <li key={s.id} className={`flex items-center gap-3 rounded-lg border p-2.5 ${s.ativa ? "border-purple-400/60 bg-purple-900/30" : "border-pedra-700 bg-pedra-950/50"}`}>
              <IconeCriatura c={porId.get(s.tipo)} id={s.tipo} tam={32} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{s.chefe && "☠ "}{s.nome} <span className="text-xs text-purple-300">Nv {s.nivel}</span></p>
                <p className="text-xs text-pedra-500">{s.abates} abates · {Math.round((0.4 + 0.03 * Math.min(10, s.nivel)) * 100)}% da força{s.ativa ? " · em campo" : ""}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ admin

function Admin({ online, enviando, acao, f }: { online: boolean; enviando: boolean; acao: (a: string, v?: string | number, alvo?: string, t?: string) => void; f: Ficha }) {
  const campo = "rounded border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm";
  const botao = "rounded border border-ouro/50 px-3 py-1 text-sm text-ouro hover:bg-ouro/15 disabled:opacity-40";
  const linha = (titulo: string, desc: string, nome: string, acaoNome: string, rotulo: string, padrao?: string | number, extra?: React.ReactNode) => (
    <form className="flex flex-wrap items-center gap-2 border-b border-pedra-800 py-2.5" onSubmit={(e) => { e.preventDefault(); acao(acaoNome, String(new FormData(e.currentTarget).get(nome) ?? "")); }}>
      <div className="min-w-48 flex-1"><p className="text-sm">{titulo}</p><p className="text-xs text-pedra-500">{desc}</p></div>
      {extra ?? <input name={nome} type="number" defaultValue={padrao} className={`${campo} w-28 text-right font-mono`} />}
      <button disabled={!online || enviando} className={botao}>{rotulo}</button>
    </form>
  );
  return (
    <Cartao titulo="Ferramentas de admin">
      {!online && <p className="mb-3 rounded-lg border border-yellow-500/40 bg-yellow-950/30 p-2.5 text-sm text-yellow-200">O jogador está offline: as alterações só funcionam com ele dentro do servidor.</p>}
      {linha("Definir nível", `Hoje: ${f.nivel} (máx. ${f.nivelMax})`, "v", "nivel", "Definir", f.nivel)}
      {linha("Dar XP", "Soma XP e sobe de nível se passar", "v", "xp", "Dar", 500)}
      {linha("Dar moedas", "Em cobre (100 = 1 ouro)", "v", "moedas", "Dar", 1000)}
      {linha("Tirar moedas", "Em cobre, da bolsa", "v", "tirarmoedas", "Tirar", 100)}
      {linha("Saldo do banco", `Hoje: ${f.banco}`, "v", "banco", "Definir", f.bancoCobre ?? 0)}
      {linha("Trocar classe", `Hoje: ${f.classe || "nenhuma"}`, "v", "classe", "Trocar", undefined,
        <select name="v" defaultValue={f.classeId?.toLowerCase() || "nenhuma"} className={campo}>
          {[...CLASSES, "nenhuma"].map((c) => <option key={c} value={c}>{c}</option>)}
        </select>)}
      <form className="flex flex-wrap items-center gap-2 border-b border-pedra-800 py-2.5" onSubmit={(e) => {
        e.preventDefault(); const d = new FormData(e.currentTarget); acao("dar", String(d.get("q") ?? "1"), String(d.get("id") ?? "").trim());
      }}>
        <div className="min-w-48 flex-1"><p className="text-sm">Dar item</p><p className="text-xs text-pedra-500">ID do item, ex.: minecraft:diamond_sword ou frxworld:moeda_ouro</p></div>
        <input name="id" placeholder="minecraft:diamond" className={`${campo} w-56 font-mono`} />
        <input name="q" type="number" defaultValue={1} min={1} max={6400} className={`${campo} w-20 text-right font-mono`} />
        <button disabled={!online || enviando} className={botao}>Dar</button>
      </form>
      <div className="flex flex-wrap items-center gap-2 py-2.5">
        <div className="min-w-48 flex-1"><p className="text-sm">Ações rápidas</p><p className="text-xs text-pedra-500">Modo de jogo atual: {f.modo}</p></div>
        <button disabled={!online || enviando} onClick={() => acao("curar")} className={botao}>❤️ Curar</button>
        {["survival", "creative", "adventure", "spectator"].map((m) => (
          <button key={m} disabled={!online || enviando || f.modo === m} onClick={() => acao("modo", m)} className={botao}>{m}</button>
        ))}
      </div>
      <p className="mt-2 text-xs text-pedra-500">Habilidades, talentos e reputação se ajustam nas próprias abas. Cada ação vira um comando no console do servidor (aparece na aba Servidor).</p>
    </Cartao>
  );
}
