import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

const schema = z.object({
  uuid: z.string().min(1).max(40),
  ativo: z.number().int().min(1).max(3),
  vagas: z.array(z.object({
    vaga: z.number().int().min(1).max(3),
    idJogo: z.string().max(40).optional(),
    nome: z.string().max(20),
    classe: z.string().max(30),
    raca: z.string().max(40),
    nivel: z.number().int().min(0),
    rank: z.string().max(4),
  })).max(3),
});

/** O servidor manda os personagens de uma conta sempre que o índice dela muda (login, saída, troca, novo nome). */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  const { uuid, ativo, vagas } = dados.data;
  for (const v of vagas) {
    const campos = { idJogo: v.idJogo ?? "", nome: v.nome, classe: v.classe, raca: v.raca, nivel: Math.max(1, v.nivel), rank: v.rank, emUso: v.vaga === ativo };
    await prisma.personagem.upsert({ where: { uuid_vaga: { uuid, vaga: v.vaga } }, create: { uuid, vaga: v.vaga, ...campos }, update: campos });
  }
  return NextResponse.json({ ok: true });
}
