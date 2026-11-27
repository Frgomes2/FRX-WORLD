import type { Metadata } from "next";
import { chefesDoSite, dadosPublicos } from "@/lib/publico";
import type { ChefePublico } from "@/lib/chefes";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { Falta } from "@/components/publico/chefes";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Chefes",
  description: "Os chefes das masmorras dos reinos e da Floresta do Crepúsculo no FRX-WORLD: quem está vivo, quem foi derrotado, quanto falta para voltar e quem deu o golpe final.",
  alternates: { canonical: "/chefes" },
};

function tempoLuta(seg: number) {
  if (seg < 0) return null;
  return seg < 60 ? `${seg} s` : `${Math.floor(seg / 60)} min ${String(seg % 60).padStart(2, "0")} s`;
}

function quando(ms: number, agora: number) {
  const min = Math.floor((agora - ms) / 60000);
  if (min < 1) return "agora há pouco";
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  const d = Math.floor(h / 24);
  return `há ${d} ${d === 1 ? "dia" : "dias"}`;
}

function Cartao({ c, agora }: { c: ChefePublico; agora: number }) {
  return (
    <article className={`frx-card frx-revela rounded-xl border p-4 ${c.vivo ? "border-red-900/70 bg-gradient-to-br from-pedra-900 to-red-950/40" : "border-pedra-700 bg-pedra-900/70"}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wider text-pedra-500">{c.lugar} · {c.covil}</p>
          <h3 className="mt-0.5 font-titulo text-xl text-ouro">{c.nome}{c.geracao > 0 && <span className="ml-2 text-base text-fuchsia-300">✦{c.geracao}</span>}</h3>
        </div>
        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${c.vivo ? "bg-red-900/60 text-red-200 frx-pulsa" : "bg-pedra-800 text-pedra-400"}`}>{c.vivo ? "☠ Vivo" : "Derrotado"}</span>
      </div>
      {!c.vivo && (
        <p className="mt-3 text-sm text-pergaminho/80">
          Volta em <span className="font-semibold text-ouro">{c.voltaEm ? <Falta alvo={c.voltaEm} servidor={agora} /> : "breve"}</span>
          {c.geracao > 0 && <span className="text-fuchsia-200"> · mais forte (✦{c.geracao})</span>}
        </p>
      )}
      {c.vivo && <p className="mt-3 text-sm text-pergaminho/80">{c.geracao > 0 ? `Voltou mais forte: +${25 * c.geracao}% de vida e +${15 * c.geracao}% de dano.` : "Esperando quem tenha coragem."}</p>}
      {c.ultima && (
        <p className="mt-2 border-t border-pedra-800 pt-2 text-xs text-pedra-500">
          Última queda {quando(c.ultima.quando, agora)}
          {c.ultima.matador && <> · golpe final de <span className="text-pergaminho">{c.ultima.matador}</span></>}
          {tempoLuta(c.ultima.duracao) && <> · luta de {tempoLuta(c.ultima.duracao)}</>}
        </p>
      )}
    </article>
  );
}

export default async function Chefes() {
  const [dados, chefes] = await Promise.all([dadosPublicos().catch(() => null), Promise.resolve(chefesDoSite())]);
  const agora = Date.now();
  const reinos = chefes.filter((c) => c.grupo === "reino");
  const tf = chefes.filter((c) => c.grupo === "crepusculo");
  const vivos = chefes.filter((c) => c.vivo).length;
  return (
    <Casca pagina="chefes" inicial={dados}>
      <CabecaPagina icone="☠️" sobre="Masmorras dos reinos e Floresta do Crepúsculo" titulo="Chefes"
        texto={`Cada chefe derrotado volta depois de um tempo, e volta mais forte. ${vivos} ${vivos === 1 ? "está vivo" : "estão vivos"} agora.`} />
      <Miolo>
        <section>
          <h2 className="mb-1 font-titulo text-3xl">Masmorras dos reinos</h2>
          <p className="mb-4 text-sm text-pedra-500">Voltam 24 horas depois de derrotados: morreu hoje, amanhã está de volta. A cada volta: +25% de vida e +15% de dano (até ✦10), e quem mais ferir leva prêmios extras. Se ficar 3 dias sem ninguém derrotar, cai uma geração.</p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{reinos.map((c) => <Cartao key={c.chave} c={c} agora={agora} />)}</div>
        </section>
        <section className="mt-10">
          <h2 className="mb-1 font-titulo text-3xl">Floresta do Crepúsculo</h2>
          <p className="mb-4 text-sm text-pedra-500">Os chefes do Twilight Forest voltam ao covil 24 horas depois de derrotados, também mais fortes a cada volta.</p>
          {tf.length ? <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{tf.map((c) => <Cartao key={c.chave} c={c} agora={agora} />)}</div>
            : <p className="rounded-xl border border-pedra-700 bg-pedra-900/60 p-4 text-sm text-pedra-500">Nenhum chefe da Floresta do Crepúsculo caiu ainda. Quem será o primeiro?</p>}
        </section>
      </Miolo>
    </Casca>
  );
}
