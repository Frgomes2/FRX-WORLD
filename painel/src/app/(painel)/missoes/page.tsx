import { prisma } from "@/lib/prisma";
import { lerConfig } from "@/lib/config-jogo";
import { FormTaverneiro } from "./taverneiro";
import { QuadroMissoes } from "./quadro";

export default async function MissoesPage() {
  const missoes = await prisma.missao.findMany({ orderBy: [{ nivelMin: "asc" }, { ordem: "asc" }] });
  const { taverna } = await lerConfig();
  return (
    <>
      <QuadroMissoes missoes={missoes.map((m) => ({
        id: m.id, titulo: m.titulo, descricao: m.descricao, tipo: m.tipo, alvo: m.alvo, quantidade: m.quantidade, nivelMin: m.nivelMin,
        recompensaXp: m.recompensaXp, recompensaMoedas: m.recompensaMoedas, recompensaItens: (m.recompensaItens as { item: string; qtd: number }[]) ?? [],
        repetivel: m.repetivel, intervaloHoras: m.intervaloHoras, preRequisitoId: m.preRequisitoId, ativa: m.ativa, ordem: m.ordem,
      }))} />
      <FormTaverneiro nome={taverna.nome} falas={taverna.falas} />
    </>
  );
}
