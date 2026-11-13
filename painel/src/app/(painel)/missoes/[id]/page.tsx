import { notFound } from "next/navigation";
import { prisma } from "@/lib/prisma";
import { EditorMissao } from "./editor";

export default async function EditarMissao({ params }: PageProps<"/missoes/[id]">) {
  const { id } = await params;
  const outras = (await prisma.missao.findMany({ orderBy: { nivelMin: "asc" }, select: { id: true, titulo: true, nivelMin: true } }))
    .filter((o) => o.id !== id);

  const m = id === "nova" ? null : await prisma.missao.findUnique({ where: { id } });
  if (id !== "nova" && !m) notFound();

  const valores = m
    ? { titulo: m.titulo, descricao: m.descricao, tipo: m.tipo, alvo: m.alvo, quantidade: m.quantidade, nivelMin: m.nivelMin, rankMin: m.rankMin,
        recompensaXp: m.recompensaXp, recompensaMoedas: m.recompensaMoedas, recompensaItens: (m.recompensaItens as { item: string; qtd: number }[]) ?? [],
        repetivel: m.repetivel, intervaloHoras: m.intervaloHoras, preRequisitoId: m.preRequisitoId, ativa: m.ativa, ordem: m.ordem }
    : { titulo: "", descricao: "", tipo: "MATAR", alvo: "minecraft:zombie", quantidade: 10, nivelMin: 1, rankMin: 0,
        recompensaXp: 100, recompensaMoedas: 20, recompensaItens: [], repetivel: false, intervaloHoras: 24, preRequisitoId: null, ativa: true, ordem: 0 };

  return <EditorMissao id={m?.id ?? null} inicial={valores} outras={outras} />;
}
