import { readFile } from "node:fs/promises";
import path from "node:path";
import { notFound } from "next/navigation";
import { prisma } from "@/lib/prisma";
import { FichaJogador } from "./ficha";

export default async function JogadorPage({ params }: { params: Promise<{ uuid: string }> }) {
  const { uuid } = await params;
  const j = await prisma.jogador.findUnique({ where: { uuid } });
  if (!j) notFound();
  // admin do servidor = está no ops.json do Minecraft
  const ops = await readFile(path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server", "ops.json"), "utf8")
    .then((t) => JSON.parse(t) as { uuid?: string; name?: string }[]).catch(() => []);
  const ehOp = ops.some((o) => o.uuid === j.uuid || o.name?.toLowerCase() === j.nick.toLowerCase());
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
      inicialOp={ehOp}
    />
  );
}
