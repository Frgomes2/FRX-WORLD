import { notFound } from "next/navigation";
import { prisma } from "@/lib/prisma";
import { FichaJogador } from "./ficha";

export default async function JogadorPage({ params }: { params: Promise<{ uuid: string }> }) {
  const { uuid } = await params;
  const j = await prisma.jogador.findUnique({ where: { uuid } });
  if (!j) notFound();
  return (
    <FichaJogador
      base={{
        uuid: j.uuid, nick: j.nick, nivel: j.nivel, rank: j.rank, classe: j.classe, titulo: j.titulo, conquistas: j.conquistas,
        mortes: j.mortes, moedas: j.moedas, banco: j.banco, ativo: j.ativo, criadoEm: j.criadoEm.getTime(), ultimoLogin: j.ultimoLogin?.getTime() ?? null,
      }}
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      inicial={(j.detalhe as any) ?? null}
      inicialEm={j.detalheEm?.getTime() ?? null}
      inicialOnline={j.online}
    />
  );
}
