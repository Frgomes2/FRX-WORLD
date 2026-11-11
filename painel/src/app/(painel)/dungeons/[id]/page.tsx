import { notFound } from "next/navigation";
import { prisma } from "@/lib/prisma";
import { lerAndares } from "@/lib/dungeons";
import { EditorDungeon } from "./editor";

export default async function EditarDungeon({ params }: PageProps<"/dungeons/[id]">) {
  const { id } = await params;
  const d = id === "nova" ? null : await prisma.dungeon.findUnique({ where: { id } });
  if (id !== "nova" && !d) notFound();

  const padrao = (n: number) => ({ bossTipo: "minecraft:zombie", bossNome: n === 1 ? "Sentinela" : "Carcereiro",
    bossVida: n === 1 ? 50 : 80, bossDano: n === 1 ? 4 : 6, bossVelocidade: 1, lacaioTipo: "minecraft:zombie", lacaioQtd: 2 });
  const andares = d ? lerAndares(d.andares) : [];
  const valores = {
    nome: d?.nome ?? "", descricao: d?.descricao ?? "", distanciaMin: d?.distanciaMin ?? 0, distanciaMax: d?.distanciaMax ?? 1000,
    nivelRecomendado: d?.nivelRecomendado ?? 1, bossTipo: d?.bossTipo ?? "minecraft:zombie", bossNome: d?.bossNome ?? "Guardião",
    bossVida: d?.bossVida ?? 100, bossDano: d?.bossDano ?? 6, bossVelocidade: d?.bossVelocidade ?? 1, lacaioTipo: d?.lacaioTipo ?? "minecraft:zombie",
    lacaioQtd: d?.lacaioQtd ?? 3, recompensaXp: d?.recompensaXp ?? 500, recargaHoras: d?.recargaHoras ?? 24, ativa: d?.ativa ?? true,
    andares: [andares[0] ?? padrao(1), andares[1] ?? padrao(2)],
    loot: (Array.isArray(d?.loot) ? d.loot : []) as { item: string; qtd: number; chance: number }[],
  };
  const outras = (await prisma.dungeon.findMany({ select: { id: true, nome: true, distanciaMin: true, distanciaMax: true, ativa: true } })).filter((o) => o.id !== d?.id);
  return <EditorDungeon id={d?.id ?? null} inicial={valores} outras={outras} />;
}
