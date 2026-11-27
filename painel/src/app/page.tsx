import type { Metadata } from "next";
import { dadosPublicos } from "@/lib/publico";
import { NOTAS } from "@/lib/notas";
import { livros } from "@/lib/livros";
import { paginaExpedicoes } from "@/lib/expedicoes";
import { Casca, Miolo } from "@/components/publico/casca";
import { SecaoAgora, SecaoCronicas } from "@/components/publico/blocos";
import { ChamadaEntrar, ExpedicaoAoVivo, Explorar, Heroi, Pilares } from "@/components/publico/inicio";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: { absolute: "FRX-WORLD · Servidor de Minecraft MMORPG e RPG (modpack Forge 1.20.1)" },
  alternates: { canonical: "/" },
};

/** Dados estruturados (schema.org) para o Google entender que é um jogo/servidor de Minecraft RPG. */
const ESQUEMA = {
  "@context": "https://schema.org",
  "@graph": [
    { "@type": "WebSite", "@id": "https://craft.frgomes.com.br/#site", url: "https://craft.frgomes.com.br", name: "FRX-WORLD", inLanguage: "pt-BR",
      description: "Servidor brasileiro de Minecraft MMORPG e RPG com modpack Forge 1.20.1." },
    { "@type": "VideoGame", name: "FRX-WORLD", url: "https://craft.frgomes.com.br", inLanguage: "pt-BR",
      genre: ["MMORPG", "RPG", "Fantasia"], gamePlatform: "PC", playMode: "MultiPlayer", applicationCategory: "Game",
      description: "Modpack e servidor de Minecraft MMORPG: 13 classes, raças, Guilda com ranks de F a S, reinos com NPCs, chefes, Torre Infinita, duelos X1 e expedições que se renovam.",
      isBasedOn: { "@type": "VideoGame", name: "Minecraft: Java Edition" },
      author: { "@type": "Person", name: "Flavio Gomes", url: "https://frgomes.com.br" } },
  ],
};

export default async function Inicio() {
  const [dados, biblioteca, exp] = await Promise.all([
    dadosPublicos().catch(() => null),
    livros().catch(() => []),
    paginaExpedicoes().catch(() => null),
  ]);
  return (
    <Casca pagina="inicio" inicial={dados}>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(ESQUEMA) }} />
      <Heroi />
      <Miolo>
        <ExpedicaoAoVivo atual={exp?.atual ?? null} agora={exp?.agora ?? Date.now()} />
        <SecaoAgora />
        <Pilares />
        <Explorar ultima={NOTAS[0] ? { versao: NOTAS[0].versao, titulo: NOTAS[0].titulo } : null} livros={biblioteca.length} />
        <div>
          <SecaoCronicas limite={4} />
          <p className="mt-3 text-right"><a href="/aventureiros#cronicas" className="text-sm text-ouro-escuro hover:text-ouro">Ver todas as crônicas →</a></p>
        </div>
        <ChamadaEntrar />
      </Miolo>
    </Casca>
  );
}
