import Link from "next/link";
import type { MapaPublico as DadosMapa, PerfilPublico } from "@/lib/publico";
import { Moedas } from "@/components/itens";
import { COR_RANK, ICONE_CLASSE, SeloRank, Skin } from "@/components/publico/jogadores";
import { MapaPublico } from "@/components/publico/mapa";
import { Vagalumes } from "@/components/publico/efeitos";

const COR_CLASSE: Record<string, string> = {
  Guerreiro: "#c0563f", Arqueiro: "#6fae4e", Mago: "#5a8fe6", "Clérigo": "#e8d27a", Ladino: "#8f8f9c", Assassino: "#7a4fb5",
  Invocador: "#4fb5a6", Paladino: "#e0b04a", Bardo: "#e67fb0", Monge: "#e08a3c", Druida: "#4f9f5f", Samurai: "#d94a4a", Cartomante: "#4fd6e8",
};
const ICONE_CRONICA_PADRAO = "✦";

export type PersonagemConta = { vaga: number; nome: string; classe: string; raca: string; nivel: number; rank: string; emUso: boolean; temFicha: boolean };
export type TerrenoConta = { x: number; z: number; largura: number; fundo: number; pago: number };
export type Posicoes = { fortes: number; ricos: number; conquistas: number; semana: number; mudou: number };

const tempo = (min: number) => (min >= 60 ? `${Math.floor(min / 60)} h ${min % 60} min` : `${min} min`);
const milhar = (n: number) => n.toLocaleString("pt-BR");
const dia = (ms: number) => new Date(ms).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit" });

function Numero({ valor, rotulo, nota }: { valor: React.ReactNode; rotulo: string; nota?: string }) {
  return (
    <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-3 py-3 text-center">
      <p className="font-titulo text-2xl text-ouro">{valor}</p>
      <p className="text-xs text-pedra-500">{rotulo}</p>
      {nota ? <p className="mt-0.5 text-[11px] text-pergaminho/70">{nota}</p> : null}
    </div>
  );
}

function Bloco({ titulo, children, className = "" }: { titulo: string; children: React.ReactNode; className?: string }) {
  return (
    <section className={`frx-surge rounded-2xl border border-pedra-700 bg-pedra-900 p-4 sm:p-5 ${className}`}>
      <h2 className="mb-3 font-titulo text-lg text-ouro">{titulo}</h2>
      {children}
    </section>
  );
}

/** Página da conta do jogador: cabeçalho do personagem, jornada, história, coleção, dinheiro e terrenos. Tudo com o que o site já recebe. */
export function PainelConta({ nick, perfil, personagens, vagaVista, xpProximo, posicoes, banco, bolsa, terrenos, mapa }: {
  nick: string; perfil: PerfilPublico | null; personagens: PersonagemConta[]; vagaVista: number; xpProximo: number; posicoes: Posicoes;
  banco: number; bolsa: number; terrenos: TerrenoConta[]; mapa: DadosMapa | null;
}) {
  const p = perfil;
  const corRank = COR_RANK[p?.rank ?? "F"] ?? "#8b8378";
  const corClasse = COR_CLASSE[p?.classe ?? ""] ?? "#d4a93a";
  const pct = p ? Math.max(0, Math.min(100, Math.round((p.xp / Math.max(1, xpProximo)) * 100))) : 0;
  const raca = p?.raca ? `${p.raca.principal}${p.raca.secundaria && p.raca.secundaria !== p.raca.principal ? ` / ${p.raca.secundaria}` : ""}` : "";
  const maiorRep = Math.max(1, ...(p?.reputacao ?? []).map((r) => Math.abs(r.pontos)));

  return (
    <div className="space-y-5">
      {/* cabeçalho do personagem */}
      <section className="frx-surge relative overflow-hidden rounded-2xl border border-pedra-700"
        style={{ background: `radial-gradient(ellipse at 15% 0%, ${corClasse}40, transparent 60%), linear-gradient(135deg, ${corRank}26, #17140f 55%)` }}>
        <div className="frx-nevoa" aria-hidden="true" />
        <Vagalumes quantos={10} />
        <div className="relative flex flex-col items-center gap-5 p-5 sm:flex-row sm:items-end sm:p-7">
          <div className="relative shrink-0">
            <span className="frx-runa" style={{ borderColor: `${corClasse}66` }} aria-hidden="true" />
            <div className="frx-aura rounded-xl border border-pedra-700 bg-pedra-950/60 p-3" style={{ ["--cor" as string]: `${corClasse}55` }}>
              <div className="frx-flutua"><Skin nick={nick} modo="corpo" tam={132} /></div>
            </div>
          </div>
          <div className="min-w-0 flex-1 text-center sm:text-left">
            {p?.titulo ? <p className="text-sm italic text-ouro/90">{p.titulo}</p> : null}
            <h1 className="font-titulo text-4xl leading-tight text-pergaminho">{p?.personagem || nick}</h1>
            <p className="mt-1 flex flex-wrap items-center justify-center gap-2 text-sm text-pergaminho/80 sm:justify-start">
              <SeloRank rank={p?.rank ?? "F"} />
              <span style={{ color: corClasse }}>{ICONE_CLASSE[p?.classe ?? ""] ?? "🎒"} {p?.classe || "Sem classe"}</span>
              {raca ? <span>· {raca}</span> : null}
              {p?.online ? <span className="rounded-full border border-emerald-700 bg-emerald-950/60 px-2 text-xs text-emerald-300">● no mundo agora</span> : null}
              {p && !p.personagemEmUso ? <span className="rounded-full border border-pedra-700 px-2 text-xs text-pedra-500">fora de uso</span> : null}
            </p>
            {p?.clas.length ? (
              <p className="mt-2 flex flex-wrap justify-center gap-1.5 sm:justify-start">
                {p.clas.map((c) => <span key={c.nome} className="rounded-md border px-2 py-0.5 text-xs" style={{ borderColor: c.cor || "#6b5f50", color: c.cor || "#e9dfc8" }}>⚑ {c.nome} · {c.cargo}</span>)}
              </p>
            ) : null}
            <div className="mt-4">
              <p className="mb-1 flex justify-between text-xs text-pedra-500">
                <span>Nível <b className="text-base text-pergaminho">{p?.nivel ?? 1}</b></span>
                <span>{milhar(p?.xp ?? 0)} / {milhar(xpProximo)} XP · {pct}%</span>
              </p>
              <div className="h-3 overflow-hidden rounded-full border border-pedra-700 bg-pedra-950">
                <div className="frx-enche h-full rounded-full" style={{ width: `${pct}%`, background: `linear-gradient(90deg, ${corClasse}, #d4a93a)` }} />
              </div>
              {p?.guilda.proximo ? <p className="mt-1 text-xs text-pedra-500">Guilda: rank {p.rank} · missões rendem +{p.guilda.bonusMissao}% · próximo rank: {p.guilda.proximo}</p> : p ? <p className="mt-1 text-xs text-pedra-500">Guilda: rank máximo.</p> : null}
            </div>
          </div>
        </div>
      </section>

      {/* personagens */}
      {personagens.length > 1 && (
        <section className="grid gap-3 sm:grid-cols-3">
          {personagens.map((x) => {
            const ativo = vagaVista ? vagaVista === x.vaga : x.emUso;
            const cor = COR_CLASSE[x.classe] ?? "#6b5f50";
            return (
              <Link key={x.vaga} href={x.emUso ? "/conta" : `/conta?vaga=${x.vaga}`}
                className={`frx-surge flex items-center gap-3 rounded-xl border p-3 transition hover:border-ouro ${ativo ? "border-ouro bg-pedra-800" : "border-pedra-700 bg-pedra-900"}`}>
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-lg text-2xl" style={{ background: `${cor}26`, border: `1px solid ${cor}` }}>{ICONE_CLASSE[x.classe] ?? "🎒"}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold">{x.nome}</span>
                  <span className="block text-xs text-pedra-500">{x.classe || "sem classe"} · Nv {x.nivel}{x.rank ? ` · ${x.rank}` : ""}</span>
                  {!x.temFicha ? <span className="block text-[11px] text-pedra-500">A ficha aparece depois que você jogar com ele.</span> : null}
                </span>
                <span className={`text-[11px] ${x.emUso ? "text-ouro" : "text-pedra-500"}`}>{x.emUso ? "em uso" : "guardado"}</span>
              </Link>
            );
          })}
        </section>
      )}

      {/* jornada */}
      <Bloco titulo="Sua jornada">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <Numero valor={p?.stats ? tempo(p.stats.tempoJogoMin) : "—"} rotulo="tempo de jogo" />
          <Numero valor={p?.stats ? milhar(p.stats.mobsMortos) : "—"} rotulo="monstros derrotados" />
          <Numero valor={p?.stats ? `${(p.stats.blocosAndados / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} km` : "—"} rotulo="distância percorrida" />
          <Numero valor={milhar(p?.conquistas ?? 0)} rotulo="conquistas" nota={posicoes.conquistas ? `${posicoes.conquistas}º do servidor` : undefined} />
          <Numero valor={milhar(p?.mortes ?? 0)} rotulo="mortes" />
          <Numero valor={posicoes.fortes ? `${posicoes.fortes}º` : "—"} rotulo="entre os mais fortes"
            nota={posicoes.fortes ? `${posicoes.semana > 0 ? `+${posicoes.semana} níveis na semana` : "sem níveis novos na semana"}${posicoes.mudou ? ` · ${posicoes.mudou > 0 ? `subiu ${posicoes.mudou}` : `caiu ${-posicoes.mudou}`}` : ""}` : undefined} />
        </div>
        <div className="mt-3 grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">🗼 Torre Infinita</p>
            <p className="mt-1 text-sm">{p?.torre ? <>Recorde: andar <b className="text-ouro">{p.torre.recorde}</b> · {milhar(p.torre.abates)} abates</> : "Você ainda não entrou na Torre."}</p>
          </div>
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">🏆 Temporada</p>
            <p className="mt-1 text-sm">{p?.temporada ? <><b className="text-ouro">{p.temporada.posicao}º</b> lugar · {milhar(p.temporada.pts)} pontos</> : "Sem pontos nesta temporada."}</p>
          </div>
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">✦ Habilidade única</p>
            <p className="mt-1 text-sm">{p?.unica ? <><b className="text-ouro">{p.unica.nome}</b> · nível {p.unica.nivel}</> : "Ainda não despertou."}</p>
            {p?.unica?.descricao ? <p className="mt-0.5 text-xs text-pedra-500">{p.unica.descricao}</p> : null}
          </div>
        </div>
      </Bloco>

      {/* expedições e habilidades */}
      <div className="grid gap-5 lg:grid-cols-2">
        <Bloco titulo="🧭 Suas expedições" className="relative overflow-hidden">
          {p?.expedicoes && p.expedicoes.participacoes > 0 ? (
            <>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
                <Numero valor={milhar(p.expedicoes.participacoes)} rotulo="expedições" />
                <Numero valor={milhar(p.expedicoes.mvps)} rotulo="vezes MVP" />
                <Numero valor={milhar(p.expedicoes.chefes)} rotulo="chefes" />
                <Numero valor={milhar(p.expedicoes.estruturas)} rotulo="estruturas" />
                <Numero valor={`${p.expedicoes.km2.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}`} rotulo="km² explorados" />
              </div>
              {p.expedicoes.ultimas.length ? (
                <ul className="mt-3 space-y-1.5">
                  {p.expedicoes.ultimas.map((e) => (
                    <li key={e.numero}>
                      <Link href={`/expedicoes/${e.numero}`} className="frx-card flex items-center justify-between rounded-lg border border-emerald-900/70 bg-[#12201b] px-3 py-2 text-sm transition hover:border-emerald-500">
                        <span>Expedição <b className="text-emerald-200">#{e.numero}</b></span>
                        <span className={e.posicao === 1 ? "text-ouro" : "text-pedra-500"}>{e.posicao === 1 ? "🏆 MVP" : `${e.posicao}º`} de {e.total}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : null}
            </>
          ) : (
            <p className="text-sm text-pedra-500">Você ainda não foi a nenhuma expedição. A partir do rank D, o pórtico das Terras Inexploradas leva a uma área nova a cada 2 dias. <Link href="/expedicoes" className="text-emerald-300 hover:underline">Ver a expedição de agora →</Link></p>
          )}
        </Bloco>

        <Bloco titulo={`✨ Suas habilidades${p?.totalHabilidades ? ` (${p.habilidades.length} de ${p.totalHabilidades})` : ""}`}>
          {p?.habilidades.length ? (
            <ul className="grid gap-2 sm:grid-cols-2">
              {[...p.habilidades].sort((a, b) => Number(b.ultimate) - Number(a.ultimate) || b.nivel - a.nivel).slice(0, 8).map((h) => (
                <li key={h.nome} title={h.descricao} className={`frx-card rounded-lg border px-3 py-2 ${h.ultimate ? "border-ouro-escuro bg-ouro/5" : "border-pedra-700 bg-pedra-900/80"}`}>
                  <p className="flex items-center justify-between gap-2 text-sm">
                    <span className={`truncate font-semibold ${h.ultimate ? "text-ouro" : ""}`}>{h.ultimate ? "★ " : ""}{h.nome}</span>
                    <span className="shrink-0 text-xs text-pedra-500">Nv {h.nivel}</span>
                  </p>
                  <div className="mt-1 h-1 overflow-hidden rounded bg-pedra-950"><div className="frx-enche h-full rounded" style={{ width: `${Math.min(100, h.nivel * 10)}%`, background: corClasse }} /></div>
                  <p className="mt-0.5 text-[11px] text-pedra-500">{milhar(h.usos)} usos</p>
                </li>
              ))}
            </ul>
          ) : <p className="text-sm text-pedra-500">Escolha uma classe no jogo para liberar as habilidades.</p>}
        </Bloco>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* história */}
        <Bloco titulo="Sua história">
          {p?.historico.length ? (
            <ol className="relative ml-2 space-y-3 border-l border-pedra-700 pl-5">
              {p.historico.slice(0, 12).map((h, i) => (
                <li key={i} className="relative">
                  <span className={`absolute -left-[29px] grid h-5 w-5 place-items-center rounded-full border text-[11px] ${h.destaque ? "border-ouro bg-ouro/20" : "border-pedra-700 bg-pedra-900"}`}>{h.icone || ICONE_CRONICA_PADRAO}</span>
                  <p className={`text-sm ${h.destaque ? "text-ouro" : "text-pergaminho"}`}>{h.texto}</p>
                  <p className="text-[11px] text-pedra-500">{dia(h.quando)}</p>
                </li>
              ))}
            </ol>
          ) : <p className="text-sm text-pedra-500">Sua história começa quando você subir de nível, mudar de rank ou conquistar algo. Tudo fica registrado aqui.</p>}
        </Bloco>

        <div className="space-y-5">
          {/* coleção */}
          <Bloco titulo="Bestiário pessoal">
            {p?.colecao && p.colecao.total > 0 ? (
              <>
                <p className="mb-1 flex justify-between text-sm"><span><b className="text-ouro">{p.colecao.criaturas}</b> de {p.colecao.total} criaturas derrotadas</span>
                  <span className="text-pedra-500">{Math.round((p.colecao.criaturas / p.colecao.total) * 100)}%</span></p>
                <div className="h-2.5 overflow-hidden rounded-full border border-pedra-700 bg-pedra-950">
                  <div className="frx-enche h-full rounded-full bg-emerald-600" style={{ width: `${Math.min(100, (p.colecao.criaturas / p.colecao.total) * 100)}%` }} />
                </div>
                {p.colecao.proximoMarco > p.colecao.criaturas ? <p className="mt-1 text-xs text-pedra-500">Faltam {p.colecao.proximoMarco - p.colecao.criaturas} para o próximo marco ({p.colecao.proximoMarco}).</p> : null}
                {p.colecao.chefes.length ? (
                  <p className="mt-3 flex flex-wrap gap-1.5">{p.colecao.chefes.map((c) => <span key={c} className="rounded-md border border-red-900 bg-red-950/40 px-2 py-0.5 text-xs text-red-200">☠ {c}</span>)}</p>
                ) : <p className="mt-2 text-xs text-pedra-500">Nenhum chefe derrotado ainda.</p>}
              </>
            ) : <p className="text-sm text-pedra-500">Derrote criaturas para começar a sua coleção.</p>}
          </Bloco>

          {/* reputação */}
          <Bloco titulo="Reputação nos reinos">
            {p?.reputacao.length ? (
              <ul className="space-y-2">
                {p.reputacao.map((r) => (
                  <li key={r.reino}>
                    <p className="flex justify-between text-sm"><span>👑 {r.reino}</span><span className={r.pontos < 0 ? "text-red-300" : "text-ouro"}>{milhar(r.pontos)}</span></p>
                    <div className="h-1.5 overflow-hidden rounded-full bg-pedra-950"><div className={`frx-enche h-full rounded-full ${r.pontos < 0 ? "bg-red-700" : "bg-ouro"}`} style={{ width: `${(Math.abs(r.pontos) / maiorRep) * 100}%` }} /></div>
                  </li>
                ))}
              </ul>
            ) : <p className="text-sm text-pedra-500">Faça missões e campanhas nos reinos para ganhar reputação.</p>}
          </Bloco>
        </div>
      </div>

      {/* dinheiro e terrenos */}
      <Bloco titulo="Dinheiro e terrenos">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">Na bolsa de {p?.personagem || nick}</p>
            <p className="mt-1 text-lg"><Moedas cobre={bolsa} tam={18} /></p>
          </div>
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">No banco (da conta)</p>
            <p className="mt-1 text-lg"><Moedas cobre={banco} tam={18} /></p>
          </div>
          <div className="rounded-xl border border-pedra-700 bg-pedra-900/80 px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">Riqueza total</p>
            <p className="mt-1 text-lg"><Moedas cobre={bolsa + banco} tam={18} /></p>
            {posicoes.ricos ? <p className="text-xs text-pedra-500">{posicoes.ricos}º mais rico do servidor</p> : null}
          </div>
        </div>
        {terrenos.length === 0 ? <p className="mt-3 text-sm text-pedra-500">Você ainda não comprou nenhum terreno.</p> : (
          <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
            <ul className="space-y-2 text-sm">
              {terrenos.map((t, i) => (
                <li key={i} className="rounded-lg border border-pedra-700 px-3 py-2">
                  <p>🏠 Terreno {i + 1}: {t.largura} × {t.fundo} blocos</p>
                  <p className="text-xs text-pedra-500">perto de X {t.x}, Z {t.z}{t.pago ? <> · pago: <Moedas cobre={t.pago} tam={12} /></> : null}</p>
                </li>
              ))}
            </ul>
            {mapa ? <MapaPublico mapa={mapa} foco={[terrenos[0].x, terrenos[0].z]} marcas={terrenos.map((t, i) => ({ x: t.x, z: t.z, nome: terrenos.length > 1 ? `Seu terreno ${i + 1}` : "Seu terreno" }))} /> : null}
          </div>
        )}
      </Bloco>
    </div>
  );
}
