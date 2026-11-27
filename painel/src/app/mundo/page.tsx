import type { Metadata } from "next";
import { bestiarioPublico, dadosPublicos, mapaPublico } from "@/lib/publico";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { SecaoBestiario, SecaoMapa, SecaoMundo } from "@/components/publico/blocos";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "O Mundo",
  description: "O mundo do FRX-WORLD, servidor de Minecraft RPG: classes, Guilda, Torre Infinita, reinos, expedições, chefes mundiais, o mapa explorado e o bestiário.",
  alternates: { canonical: "/mundo" },
};

export default async function Mundo() {
  const [dados, bestiario] = await Promise.all([dadosPublicos().catch(() => null), bestiarioPublico().catch(() => [])]);
  return (
    <Casca pagina="mundo" inicial={dados}>
      <CabecaPagina icone="🌍" sobre="Reinos, chefes e terras distantes" titulo="O Mundo" texto="Tudo o que existe no FRX-WORLD, o pedaço do mapa que já foi explorado e as criaturas que esperam por você.">
        <nav className="mt-5 flex flex-wrap justify-center gap-2 text-sm">
          {[["#mundo", "Sistemas"], ["#mapa", "Mapa"], ["#bestiario", "Bestiário"]].map(([h, r]) => <a key={h} href={h} className="rounded-full border border-pedra-700 bg-pedra-950/60 px-3 py-1 hover:border-ouro hover:text-ouro">{r}</a>)}
        </nav>
      </CabecaPagina>
      <Miolo>
        <SecaoMundo />
        <SecaoMapa mapa={mapaPublico()} />
        <SecaoBestiario bestiario={bestiario} />
      </Miolo>
    </Casca>
  );
}
