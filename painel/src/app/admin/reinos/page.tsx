import { prisma } from "@/lib/prisma";
import { reinosDoServidor } from "@/lib/reinos-servidor";
import { type Campanha } from "@/lib/reinos";
import { PainelReinos } from "./painel";

function obj<T>(v: unknown): Record<string, T> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, T>) : {};
}

export default async function ReinosPage() {
  const [jogadores, skins, masmorras] = await Promise.all([
    prisma.jogador.findMany({ orderBy: [{ nivel: "desc" }, { criadoEm: "asc" }], select: { uuid: true, nick: true, nivel: true, titulo: true, classe: true, online: true, reputacao: true, campanhas: true } }),
    prisma.npcSkin.findMany({ select: { chave: true } }),
    prisma.masmorra.findMany({ select: { id: true, nome: true, x: true, z: true, colocadaX: true, colocadaZ: true, ativo: true } }),
  ]);
  const servidor = reinosDoServidor();
  return (
    <PainelReinos
      jogadores={jogadores.map((j) => ({ uuid: j.uuid, nick: j.nick, nivel: j.nivel, titulo: j.titulo, classe: j.classe, online: j.online,
        reputacao: obj<number>(j.reputacao), campanhas: obj<Campanha>(j.campanhas) }))}
      servidor={servidor}
      skins={skins.map((s) => s.chave)}
      masmorras={masmorras.filter((m) => m.ativo).map((m) => ({ id: m.id, nome: m.nome, x: m.colocadaX ?? m.x, z: m.colocadaZ ?? m.z, colocada: m.colocadaX !== null }))}
    />
  );
}
