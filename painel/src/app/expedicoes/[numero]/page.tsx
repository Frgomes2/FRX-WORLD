import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { detalheExpedicao } from "@/lib/expedicoes";
import { Contagem, dataHora } from "@/components/publico/expedicoes";
import { fmtKm2, fmtMin, Moldura, Numero, Ranking, Secao } from "../visual";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: Promise<{ numero: string }> }): Promise<Metadata> {
  const { numero } = await params;
  return { title: `Expedição #${numero} · FRX-WORLD` };
}

export default async function Expedicao({ params }: { params: Promise<{ numero: string }> }) {
  const { numero } = await params;
  const e = await detalheExpedicao(Number(numero)).catch(() => null);
  if (!e) notFound();
  const duracaoMin = Math.max(0, Math.round(((e.fim ?? e.relogio) - e.inicio) / 60_000));
  const d = e.destaques;
  const destaque = (icone: string, titulo: string, x: { nick: string; valor: number } | null, fmt: (v: number) => string) => (
    <div className="rounded-xl border border-pedra-700 bg-pedra-900/60 p-4">
      <p className="text-xs uppercase tracking-wider text-pedra-500">{icone} {titulo}</p>
      <p className="mt-1 font-semibold text-ouro">{x ? x.nick : "—"}</p>
      {x && <p className="text-sm text-pergaminho/75">{fmt(x.valor)}</p>}
    </div>
  );
  return (
    <Moldura>
      <div>
        <p className="text-xs uppercase tracking-[0.3em] text-pedra-500">Terras Inexploradas</p>
        <h1 className="font-titulo text-4xl text-ouro">Expedição #{e.numero}{e.nome ? ` · ${e.nome}` : ""}</h1>
        <p className="mt-1 text-sm text-pergaminho/75">
          {e.fim ? "Encerrada" : e.ativa ? "🟢 Em andamento" : "Fechada"} · {dataHora(e.inicio)}{e.fim ? ` até ${dataHora(e.fim)}` : ""} · {fmtMin(duracaoMin)}
          {e.versao ? ` · versão ${e.versao}` : ""} · área {e.tamanho} × {e.tamanho}
        </p>
        {e.descricao && <p className="mt-2 text-pergaminho/80">{e.descricao}</p>}
      </div>

      {!e.fim && e.ativa && (
        <div className="rounded-2xl border border-ouro-escuro bg-pedra-900 p-5 text-center">
          <p className="text-xs uppercase tracking-[0.3em] text-pedra-500">Termina em</p>
          <Contagem alvo={e.proxima} servidor={e.relogio} />
        </div>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-6">
        <Numero valor={String(e.jogadores)} rotulo="exploradores" />
        <Numero valor={String(e.estruturas)} rotulo="estruturas" />
        <Numero valor={String(e.chefes)} rotulo="chefes" />
        <Numero valor={String(e.tesouros)} rotulo="tesouros" />
        <Numero valor={String(e.mortes)} rotulo="quedas" />
        <Numero valor={fmtKm2(e.km2)} rotulo="explorados" />
      </div>

      <Secao titulo="Destaques">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {destaque("🧭", "Maior explorador", d.explorador, (v) => fmtKm2((v * 256) / 1_000_000))}
          {destaque("⚔️", "Caçador de chefes", d.chefes, (v) => `${v} chefes`)}
          {destaque("🏰", "Explorador de estruturas", d.estruturas, (v) => `${v} estruturas`)}
          {destaque("💎", "Mais tesouros", d.tesouros, (v) => `${v} baús`)}
        </div>
      </Secao>

      <Secao titulo="🏆 Ranking">
        <Ranking linhas={e.ranking} />
        <p className="mt-2 text-xs text-pedra-500">A pontuação soma área explorada, estruturas, chefes, baús de tesouro e tempo, e desconta as quedas.</p>
      </Secao>
    </Moldura>
  );
}
