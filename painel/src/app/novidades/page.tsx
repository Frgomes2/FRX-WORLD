import type { Metadata } from "next";
import { NOTAS } from "@/lib/notas";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { SecaoNovidades } from "@/components/publico/blocos";

export const metadata: Metadata = {
  title: "Novidades",
  description: "Notas de atualização do FRX-WORLD, servidor de Minecraft MMORPG: tudo o que mudou em cada versão, da mais nova para a mais antiga.",
  alternates: { canonical: "/novidades" },
};

export default function Novidades() {
  return (
    <Casca pagina="novidades">
      <CabecaPagina icone="📰" sobre={NOTAS[0] ? `Versão atual: ${NOTAS[0].versao}` : "Atualizações"} titulo="Novidades" texto="O que mudou em cada versão do servidor, da mais nova para a mais antiga." />
      <Miolo>
        <SecaoNovidades notas={NOTAS} inicio={10} />
      </Miolo>
    </Casca>
  );
}
