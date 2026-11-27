import type { Metadata } from "next";
import { modsDoPack } from "@/lib/publico";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { SecaoMods } from "@/components/publico/blocos";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Mods do modpack",
  description: "Lista completa de mods do FRX-WORLD, modpack de Minecraft RPG para Forge 1.20.1. O launcher instala tudo sozinho.",
  alternates: { canonical: "/mods" },
};

export default function Mods() {
  const mods = modsDoPack();
  return (
    <Casca pagina="mods">
      <CabecaPagina icone="🧩" sobre="Forge 1.20.1" titulo="Mods" texto={`${mods.length} mods no pack, mais o FRX-WORLD, feito só para este servidor. Você não precisa baixar nada: o launcher instala tudo.`} />
      <Miolo>
        <SecaoMods mods={mods} />
      </Miolo>
    </Casca>
  );
}
