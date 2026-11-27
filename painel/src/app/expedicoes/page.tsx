import type { Metadata } from "next";
import { paginaExpedicoes, pesos, PESOS_PADRAO } from "@/lib/expedicoes";
import { Atualizar, Contagem, Rosa } from "@/components/publico/expedicoes";
import { Casca, Miolo } from "@/components/publico/casca";
import { Skin } from "@/components/publico/jogadores";
import { Vagalumes } from "@/components/publico/efeitos";
import { data, dataHora, fmtKm2, Numero, PodioExpedicao, Ranking, Secao } from "./visual";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Expedições: Terras Inexploradas",
  description: "Expedições do FRX-WORLD, servidor de Minecraft RPG: a cada 2 dias nasce uma área nova, cheia de estruturas e masmorras. Contagem para a próxima, ranking, MVP e histórico.",
  alternates: { canonical: "/expedicoes" },
};

const PASSOS: [string, string, string][] = [
  ["🌀", "Atravesse o pórtico", "O pórtico das Terras Inexploradas fica no mundo principal. Só passa quem tem rank D ou maior na Guilda."],
  ["🗺️", "Explore o desconhecido", "Uma área de 2000 × 2000 blocos que ninguém viu, com dezenas de estruturas de mods, masmorras, chefes e baús em que cada um saqueia o seu."],
  ["💀", "Morrer não tira nada", "Quem cai volta ao mundo principal com todos os itens, mas fica 30 minutos sem poder entrar de novo."],
  ["⏳", "A cada 2 dias, tudo muda", "Às 5h a área some e outra nasce no lugar. Quem estiver lá volta para casa com tudo o que carrega."],
];

export default async function Expedicoes() {
  const [p, w] = await Promise.all([paginaExpedicoes().catch(() => null), pesos().catch(() => PESOS_PADRAO)]);
  const a = p?.atual ?? null;
  const pontos: [string, string, number][] = [
    ["🧭", "Cada chunk novo explorado", w.chunks], ["🏰", "Cada estrutura descoberta", w.estruturas], ["⚔️", "Cada chefe derrotado", w.chefes],
    ["💎", "Cada baú de tesouro", w.tesouros], ["⏱️", "Cada minuto explorando", w.minutos], ["💀", "Cada queda", w.mortes],
  ];
  return (
    <Casca pagina="expedicoes">
      <Atualizar />
      <header className="frx-exp relative overflow-hidden border-b border-emerald-800/60">
        <div className="frx-exp-mapa" aria-hidden="true" />
        <Rosa className="absolute -right-32 top-1/2 h-[30rem] w-[30rem] -translate-y-1/2 text-emerald-300/10" />
        <Vagalumes quantos={14} verde />
        <div className="relative mx-auto max-w-6xl px-4 py-12 md:py-16">
          <div className="grid gap-8 lg:grid-cols-[1.15fr_1fr] lg:items-center">
            <div>
              <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.3em] text-emerald-300">
                <span className={`h-2 w-2 rounded-full ${a ? "animate-pulse bg-emerald-400" : "bg-pedra-500"}`} />{a ? `Expedição #${a.numero} em andamento` : "Aguardando a primeira expedição"}
              </p>
              <h1 className="mt-3 font-titulo text-5xl leading-none text-pergaminho drop-shadow-[0_2px_12px_rgba(0,0,0,0.6)] md:text-7xl">Terras <span className="text-emerald-300">Inexploradas</span></h1>
              <p className="mt-4 max-w-xl text-pergaminho/80 md:text-lg">Um mundo que dura dois dias. Cada expedição abre uma área nova, nunca vista, cheia de estruturas, masmorras e tesouros. Explore o máximo que puder antes que ela suma.</p>
              <div className="mt-5 flex flex-wrap gap-3">
                <a href="#como-funciona" className="rounded-lg bg-gradient-to-b from-emerald-400 to-emerald-700 px-5 py-2.5 font-titulo uppercase tracking-[0.12em] text-pedra-950 shadow-lg shadow-black/40 transition hover:-translate-y-0.5 hover:brightness-110">Como funciona</a>
                {a && <a href="#ranking" className="rounded-lg border border-emerald-700 px-5 py-2.5 text-sm text-emerald-200 transition hover:border-emerald-300">Ranking ao vivo</a>}
              </div>
            </div>
            <div className="rounded-2xl border border-emerald-800/70 bg-pedra-950/75 p-6 text-center shadow-2xl shadow-black/50 backdrop-blur-sm">
              {a ? (
                <>
                  <div className="flex items-center justify-between gap-3">
                    <p className="font-titulo text-2xl text-emerald-200">Expedição #{a.numero}{a.nome ? ` · ${a.nome}` : ""}</p>
                    <span className="flex items-center gap-1.5 rounded-full border border-emerald-600 px-2.5 py-0.5 text-xs font-semibold text-emerald-300"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />ATIVA</span>
                  </div>
                  <p className="mt-5 text-[11px] uppercase tracking-[0.3em] text-pedra-500">A área atual some em</p>
                  <Contagem alvo={a.proxima} servidor={p!.agora} className="frx-pulsa font-titulo text-5xl tabular-nums tracking-wider text-ouro sm:text-6xl" />
                  <p className="mt-1 text-xs text-pedra-500">Aberta {dataHora(a.inicio)} · troca {dataHora(a.proxima)}</p>
                  <div className="mt-5 border-t border-pedra-800 pt-4 text-left">
                    <p className="mb-2 text-xs uppercase tracking-wider text-pedra-500">Explorando agora</p>
                    {a.agora.length ? (
                      <ul className="flex flex-wrap gap-2">
                        {a.agora.map((n) => <li key={n} className="flex items-center gap-1.5 rounded-full border border-emerald-800 bg-pedra-900 py-0.5 pl-0.5 pr-2.5 text-sm"><Skin nick={n} tam={22} className="rounded-full" />{n}</li>)}
                      </ul>
                    ) : <p className="text-sm text-pedra-500">Ninguém neste momento. A área é toda sua.</p>}
                  </div>
                </>
              ) : <p className="py-10 text-pedra-500">As Terras Inexploradas ainda não foram abertas. Fique de olho: a primeira expedição está chegando.</p>}
            </div>
          </div>
          {a && (
            <div className="mt-8 grid grid-cols-2 gap-2 sm:grid-cols-5">
              <Numero verde valor={`${a.tamanho / 1000} × ${a.tamanho / 1000} km`} rotulo="área" />
              <Numero verde valor={String(a.jogadores)} rotulo="exploradores" />
              <Numero verde valor={String(a.estruturas)} rotulo="estruturas achadas" />
              <Numero verde valor={String(a.chefes)} rotulo="chefes" />
              <Numero verde valor={fmtKm2(a.km2)} rotulo="explorados" />
            </div>
          )}
        </div>
      </header>

      <Miolo>
        {a && (
          <section id="ranking" className="scroll-mt-20">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-2">
              <div>
                <p className="text-xs uppercase tracking-[0.3em] text-emerald-400">Ao vivo</p>
                <h2 className="font-titulo text-3xl text-ouro">Ranking da expedição #{a.numero}</h2>
              </div>
              <a href={`/expedicoes/${a.numero}`} className="text-sm text-ouro-escuro hover:text-ouro">Destaques e ranking completo →</a>
            </div>
            {a.ranking.length ? (
              <>
                <PodioExpedicao linhas={a.ranking} />
                {a.ranking.length > 3 && <Ranking linhas={a.ranking.slice(3)} />}
              </>
            ) : <p className="rounded-xl border border-dashed border-pedra-700 bg-pedra-900/60 px-4 py-8 text-center text-pedra-500">Ninguém explorou esta expedição ainda. O primeiro a entrar já sai na frente.</p>}
          </section>
        )}

        <section id="como-funciona" className="scroll-mt-20">
          <p className="text-xs uppercase tracking-[0.3em] text-emerald-400">Regras da expedição</p>
          <h2 className="mb-5 font-titulo text-3xl text-ouro">Como funciona</h2>
          <ol className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {PASSOS.map(([i, t, x], n) => (
              <li key={t} className="frx-card relative overflow-hidden rounded-xl border border-emerald-900/70 bg-gradient-to-b from-[#12201b] to-pedra-900 p-5">
                <span className="absolute right-3 top-1 font-titulo text-6xl text-emerald-950">{n + 1}</span>
                <span className="frx-flutua relative inline-block text-3xl" style={{ animationDelay: `${n * 0.6}s` }} aria-hidden="true">{i}</span>
                <p className="relative mt-3 font-titulo text-xl text-emerald-200">{t}</p>
                <p className="relative mt-1 text-sm text-pergaminho/75">{x}</p>
              </li>
            ))}
          </ol>
          <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_1.2fr]">
            <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
              <h3 className="font-titulo text-xl text-ouro">🏆 Como a pontuação é feita</h3>
              <p className="mb-3 text-sm text-pedra-500">Quem soma mais pontos é o MVP da expedição.</p>
              <ul className="space-y-1.5 text-sm">
                {pontos.map(([i, r, n]) => (
                  <li key={r} className="flex items-center justify-between gap-2 rounded-md bg-pedra-800/60 px-3 py-1.5">
                    <span><span aria-hidden="true">{i}</span> {r}</span>
                    <span className={`font-mono ${n < 0 ? "text-red-400" : "text-ouro"}`}>{n > 0 ? "+" : ""}{n.toLocaleString("pt-BR")}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
              <h3 className="font-titulo text-xl text-ouro">📜 Bom saber</h3>
              <ul className="mt-3 space-y-2 text-sm text-pergaminho/80">
                <li>🧱 A área tem borda: dá para explorar 1000 blocos para cada lado a partir do centro.</li>
                <li>🚫 Lá não abrem portais do Nether nem do End. A saída é o pórtico de volta, no centro da área.</li>
                <li>🎒 Tudo o que você trouxer de volta é seu. Só o mundo da expedição some na troca.</li>
                <li>🐉 Os chefes deixam o loot de sempre, e as estruturas são sorteadas de novo a cada expedição.</li>
                <li>🗺️ No mapa do jogo aparecem o pórtico de volta e o contorno da área.</li>
              </ul>
            </div>
          </div>
        </section>

        {p && (
          <Secao titulo="Números de todas as expedições">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-6">
              <Numero valor={String(p.geral.expedicoes)} rotulo="expedições" />
              <Numero valor={p.geral.participacoes.toLocaleString("pt-BR")} rotulo="participações" />
              <Numero valor={p.geral.estruturas.toLocaleString("pt-BR")} rotulo="estruturas" />
              <Numero valor={p.geral.chefes.toLocaleString("pt-BR")} rotulo="chefes" />
              <Numero valor={fmtKm2(p.geral.km2)} rotulo="explorados" />
              <Numero valor={p.geral.tesouros.toLocaleString("pt-BR")} rotulo="tesouros" />
            </div>
          </Secao>
        )}

        <Secao titulo="Expedições anteriores">
          {p?.historico.length ? (
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {p.historico.map((e) => (
                <li key={e.numero}>
                  <a href={`/expedicoes/${e.numero}`} className="frx-card group block rounded-xl border border-pedra-700 bg-gradient-to-br from-pedra-800 to-pedra-900 p-4 transition hover:-translate-y-0.5 hover:border-emerald-600">
                    <p className="flex items-center justify-between"><span className="font-titulo text-3xl text-ouro">#{e.numero}</span><span className="text-xs text-pedra-500">{data(e.inicio)} · {e.fim ? "Encerrada" : "Fechada"}</span></p>
                    <p className="mt-1 text-sm text-pergaminho/80">{e.jogadores} exploradores · {e.estruturas} estruturas · {e.chefes} chefes · {fmtKm2(e.km2)}</p>
                    <p className="mt-2 flex items-center gap-2 text-sm">🏆 <span className="text-pedra-500">MVP:</span>{e.mvp ? <><Skin nick={e.mvp} tam={18} /><span className="text-ouro">{e.mvp}</span></> : <span className="text-pedra-500">—</span>}</p>
                  </a>
                </li>
              ))}
            </ul>
          ) : <p className="rounded-xl border border-dashed border-pedra-700 bg-pedra-900/60 px-4 py-6 text-center text-sm text-pedra-500">A primeira expedição ainda está em andamento. Quando ela terminar, o resultado fica guardado aqui para sempre.</p>}
        </Secao>
      </Miolo>
    </Casca>
  );
}
