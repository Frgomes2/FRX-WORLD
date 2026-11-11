import { prisma } from "@/lib/prisma";
import { lerAndares } from "@/lib/dungeons";
import { ListaDungeons } from "./lista";

export default async function DungeonsPage() {
  const lista = await prisma.dungeon.findMany({ orderBy: { distanciaMin: "asc" } });
  return (
    <ListaDungeons dungeons={lista.map((d) => ({
      id: d.id, nome: d.nome, descricao: d.descricao, distanciaMin: d.distanciaMin, distanciaMax: d.distanciaMax, nivelRecomendado: d.nivelRecomendado,
      bossTipo: d.bossTipo, bossNome: d.bossNome, bossVida: d.bossVida, bossDano: d.bossDano, bossVelocidade: d.bossVelocidade,
      lacaioTipo: d.lacaioTipo, lacaioQtd: d.lacaioQtd, recompensaXp: d.recompensaXp, recargaHoras: d.recargaHoras, ativa: d.ativa,
      andares: lerAndares(d.andares), loot: (Array.isArray(d.loot) ? d.loot : []) as { item: string; qtd: number; chance: number }[],
    }))} />
  );
}
