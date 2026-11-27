import type { Metadata } from "next";
import { dadosPublicos } from "@/lib/publico";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { SecaoArmas, SecaoAventureiros, SecaoCronicas, SecaoMural } from "@/components/publico/blocos";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Aventureiros",
  description: "Os aventureiros do FRX-WORLD, servidor de Minecraft RPG: fichas com classe, raça e rank, crônicas dos grandes feitos, Mural dos Primeiros e armas únicas.",
  alternates: { canonical: "/aventureiros" },
};

export default async function Aventureiros() {
  const dados = await dadosPublicos().catch(() => null);
  return (
    <Casca pagina="aventureiros" inicial={dados}>
      <CabecaPagina icone="🛡️" sobre="Salão dos heróis" titulo="Aventureiros" texto="Quem joga no FRX-WORLD, o que já fizeram e quem chegou primeiro. Clique em alguém para abrir a ficha.">
        <nav className="mt-5 flex flex-wrap justify-center gap-2 text-sm">
          {[["#aventureiros", "Fichas"], ["#cronicas", "Crônicas"], ["#mural", "Mural dos Primeiros"], ["#armas", "Armas Únicas"]].map(([h, r]) => <a key={h} href={h} className="rounded-full border border-pedra-700 bg-pedra-950/60 px-3 py-1 hover:border-ouro hover:text-ouro">{r}</a>)}
        </nav>
      </CabecaPagina>
      <Miolo>
        <SecaoAventureiros />
        <SecaoCronicas />
        <SecaoMural />
        <SecaoArmas />
      </Miolo>
    </Casca>
  );
}
