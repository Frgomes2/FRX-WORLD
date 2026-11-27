import type { Metadata } from "next";
import { dadosPublicos } from "@/lib/publico";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { SecaoClas, SecaoRankings } from "@/components/publico/blocos";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Rankings",
  description: "Rankings do FRX-WORLD, servidor de Minecraft RPG: os mais fortes, temporada do mês, Torre Infinita, pesca, corridas, duelos X1 e clãs.",
  alternates: { canonical: "/rankings" },
};

export default async function Rankings() {
  const dados = await dadosPublicos().catch(() => null);
  return (
    <Casca pagina="rankings" inicial={dados}>
      <CabecaPagina icone="🏆" sobre="Quem manda no FRX" titulo="Rankings" texto="Os mais fortes, a temporada do mês, a Torre, a pesca, as corridas, os duelos X1 e os clãs. Tudo atualiza sozinho.">
        <nav className="mt-5 flex flex-wrap justify-center gap-2 text-sm">
          {[["#rankings", "Mais fortes"], ["#temporada", "Temporada"], ["#clas", "Clãs"]].map(([h, r]) => <a key={h} href={h} className="rounded-full border border-pedra-700 bg-pedra-950/60 px-3 py-1 hover:border-ouro hover:text-ouro">{r}</a>)}
        </nav>
      </CabecaPagina>
      <Miolo>
        <SecaoRankings />
        <SecaoClas />
      </Miolo>
    </Casca>
  );
}
