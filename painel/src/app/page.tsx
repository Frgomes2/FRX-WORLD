import type { Metadata } from "next";
import { bestiarioPublico, dadosPublicos, mapaPublico, modsDoPack } from "@/lib/publico";
import { NOTAS } from "@/lib/notas";
import { livros } from "@/lib/livros";
import { SitePublico } from "@/components/publico/site";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "FRX-WORLD · Servidor de Minecraft RPG",
  description: "Status ao vivo, rankings, mapa e mods do FRX-WORLD, um MMORPG dentro do Minecraft 1.20.1.",
};

export default async function Inicio() {
  const dados = await dadosPublicos().catch(() => null);
  const biblioteca = await livros().catch(() => []);
  const bestiario = await bestiarioPublico().catch(() => []);
  return <SitePublico inicial={dados} mods={modsDoPack()} mapa={mapaPublico()} notas={NOTAS} livros={biblioteca} bestiario={bestiario} />;
}
