import { prisma } from "@/lib/prisma";
import { REINOS } from "@/lib/reinos";
import { ListaNpcs } from "./lista";

export default async function NpcsPage() {
  const skins = await prisma.npcSkin.findMany({ select: { chave: true, slim: true, atualizadoEm: true } });
  return (
    <>
      <h1 className="font-titulo text-3xl">NPCs e skins</h1>
      <p className="mb-6 text-pedra-500">
        Troque a aparência de cada tipo de NPC: envie um PNG de skin do Minecraft (64×64) ou desenhe em pixel art aqui mesmo.
        A skin de um reino vale só lá; a de &quot;Todos os reinos&quot; vale onde o reino não tiver uma própria. Os jogadores recebem em até 30 s, sem reiniciar.
      </p>
      <ListaNpcs reinos={REINOS.map((r) => ({ id: r.id, nome: r.nome }))} skins={skins.map((s) => ({ chave: s.chave, slim: s.slim, em: s.atualizadoEm.getTime() }))} />
    </>
  );
}
