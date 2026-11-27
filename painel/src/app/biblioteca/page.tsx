import type { Metadata } from "next";
import { livros } from "@/lib/livros";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";
import { Biblioteca } from "@/components/publico/biblioteca";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Biblioteca",
  description: "Biblioteca do FRX-WORLD: como funcionam os ranks, as missões, a economia, a Torre, as expedições e um livro para cada classe do servidor de Minecraft RPG.",
  alternates: { canonical: "/biblioteca" },
};

export default async function PaginaBiblioteca() {
  const lista = await livros().catch(() => []);
  return (
    <Casca pagina="biblioteca">
      <CabecaPagina icone="📚" sobre="O manual do aventureiro" titulo="Biblioteca" texto="Tudo sobre como o mundo funciona: ranks, missões, economia, a Torre, as expedições e um livro para cada classe. Clique num livro para abrir e folhear." />
      <Miolo>
        <Biblioteca livros={lista} />
      </Miolo>
    </Casca>
  );
}
