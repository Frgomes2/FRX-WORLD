import type { Metadata } from "next";
import { paginaExpedicoes } from "@/lib/expedicoes";
import { Atualizar, Contagem, data, dataHora } from "@/components/publico/expedicoes";
import { fmtKm2, Moldura, Numero, Ranking, Secao } from "./visual";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Expedições · FRX-WORLD", description: "Terras Inexploradas: a expedição atual, a contagem para a próxima, o histórico e os melhores exploradores." };

export default async function Expedicoes() {
  const p = await paginaExpedicoes().catch(() => null);
  if (!p) return <Moldura><p className="text-pedra-500">Não deu para carregar as expedições agora.</p></Moldura>;
  const a = p.atual;
  return (
    <Moldura>
      <Atualizar />
      <div className="text-center">
        <p className="text-xs uppercase tracking-[0.3em] text-pedra-500">Terras Inexploradas</p>
        <h1 className="font-titulo text-4xl text-ouro sm:text-5xl">Expedições</h1>
        <p className="mx-auto mt-2 max-w-xl text-sm text-pergaminho/75">A cada 2 dias, às 5h, abre uma área nunca vista, com estruturas e masmorras novas. Entrada pelo pórtico das Terras Inexploradas, a partir do Rank D.</p>
      </div>

      {a ? (
        <section className="rounded-2xl border border-ouro-escuro bg-gradient-to-b from-[#2a2412] to-pedra-900 p-5 sm:p-7">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="font-titulo text-3xl text-ouro">Expedição #{a.numero}{a.nome ? ` · ${a.nome}` : ""}</h2>
            <span className="flex items-center gap-1.5 rounded-full border border-green-700 px-3 py-1 text-sm text-green-400"><span className="h-2 w-2 animate-pulse rounded-full bg-green-400" />ATIVA</span>
          </div>
          <div className="mt-6 text-center">
            <p className="text-xs uppercase tracking-[0.3em] text-pedra-500">Próxima expedição em</p>
            <Contagem alvo={a.proxima} servidor={p.agora} />
            <p className="mt-1 text-xs text-pedra-500">Início {dataHora(a.inicio)} · troca {dataHora(a.proxima)} (horário de Brasília)</p>
          </div>
          <div className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-5">
            <Numero valor={`${a.tamanho} × ${a.tamanho}`} rotulo="área" />
            <Numero valor={String(a.jogadores)} rotulo="exploradores" />
            <Numero valor={String(a.estruturas)} rotulo="estruturas" />
            <Numero valor={String(a.chefes)} rotulo="chefes" />
            <Numero valor={fmtKm2(a.km2)} rotulo="explorados" />
          </div>
          <p className="mt-4 text-sm">
            👥 <span className="text-pedra-500">Na expedição agora:</span>{" "}
            {a.agora.length ? <span className="text-pergaminho">{a.agora.join(", ")}</span> : <span className="text-pedra-500">ninguém</span>}
          </p>
          <div className="mt-6">
            <h3 className="mb-2 font-titulo text-xl text-ouro">Ranking até agora</h3>
            <Ranking linhas={a.ranking} />
            <a href={`/expedicoes/${a.numero}`} className="mt-3 inline-block text-sm text-ouro-escuro hover:text-ouro">Ver tudo da expedição #{a.numero} →</a>
          </div>
        </section>
      ) : (
        <p className="rounded-xl border border-pedra-700 bg-pedra-900/60 p-5 text-center text-pedra-500">As Terras Inexploradas ainda não foram abertas.</p>
      )}

      <Secao titulo="Números do FRX-WORLD">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-6">
          <Numero valor={String(p.geral.expedicoes)} rotulo="expedições" />
          <Numero valor={p.geral.participacoes.toLocaleString("pt-BR")} rotulo="participações" />
          <Numero valor={p.geral.estruturas.toLocaleString("pt-BR")} rotulo="estruturas" />
          <Numero valor={p.geral.chefes.toLocaleString("pt-BR")} rotulo="chefes" />
          <Numero valor={fmtKm2(p.geral.km2)} rotulo="explorados" />
          <Numero valor={p.geral.tesouros.toLocaleString("pt-BR")} rotulo="tesouros" />
        </div>
      </Secao>

      <Secao titulo="Expedições anteriores">
        {p.historico.length ? (
          <ul className="grid gap-3 sm:grid-cols-2">
            {p.historico.map((e) => (
              <li key={e.numero}>
                <a href={`/expedicoes/${e.numero}`} className="block rounded-xl border border-pedra-700 bg-pedra-900/60 p-4 transition hover:border-ouro-escuro">
                  <p className="flex items-center justify-between"><span className="font-titulo text-2xl text-ouro">#{e.numero}</span><span className="text-xs text-pedra-500">{data(e.inicio)} · {e.fim ? "Encerrada" : "Fechada"}</span></p>
                  <p className="mt-1 text-sm text-pergaminho/80">{e.jogadores} exploradores · {e.estruturas} estruturas · {e.chefes} chefes · {fmtKm2(e.km2)}</p>
                  <p className="mt-1 text-sm">🏆 <span className="text-pedra-500">MVP:</span> <span className="text-ouro">{e.mvp ?? "—"}</span></p>
                </a>
              </li>
            ))}
          </ul>
        ) : <p className="text-sm text-pedra-500">A primeira expedição ainda está em andamento.</p>}
      </Secao>
    </Moldura>
  );
}
