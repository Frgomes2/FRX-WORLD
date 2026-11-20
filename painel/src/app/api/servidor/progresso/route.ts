import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";
import { registrarProgresso, retratoAntes } from "@/lib/cronicas";

const schema = z.object({
  uuid: z.string().min(1),
  vaga: z.number().int().min(1).max(3).optional(),
  nick: z.string().min(1),
  nivel: z.number().int().min(1),
  xp: z.number().int().min(0),
  rank: z.string().min(1).max(2),
  morreu: z.boolean().optional(),
  classe: z.string().max(20).optional(),
  moedas: z.number().int().min(0).optional(),
  banco: z.number().int().min(0).optional(),
  titulo: z.string().max(60).optional(),
  conquistas: z.number().int().min(0).optional(),
  reputacao: z.record(z.string().max(40), z.number().int()).optional(),
  campanhas: z.record(z.string().max(40), z.object({
    nome: z.string().max(80),
    etapa: z.number().int(),
    total: z.number().int(),
    progresso: z.number().int().optional(),
    qtd: z.number().int().optional(),
    etapaTitulo: z.string().max(120).optional(),
  })).optional(),
});

/** O servidor manda o progresso do jogador ao subir de nível, morrer e sair. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  const d = dados.data;

  // crônicas: o retrato deste personagem antes do progresso novo
  const antes = await retratoAntes(d.uuid, d.vaga, d.nick);

  const r = await prisma.jogador.updateMany({
    where: { uuid: d.uuid },
    data: {
      nick: d.nick,
      nivel: d.nivel,
      xp: Math.min(d.xp, 2_000_000_000),
      rank: d.rank,
      ...(d.classe !== undefined ? { classe: d.classe } : {}),
      ...(d.moedas !== undefined ? { moedas: d.moedas } : {}),
      ...(d.banco !== undefined ? { banco: d.banco } : {}),
      ...(d.titulo !== undefined ? { titulo: d.titulo } : {}),
      ...(d.conquistas !== undefined ? { conquistas: d.conquistas } : {}),
      ...(d.reputacao !== undefined ? { reputacao: d.reputacao } : {}),
      ...(d.campanhas !== undefined ? { campanhas: d.campanhas } : {}),
      ...(d.morreu ? { mortes: { increment: 1 } } : {}),
    },
  });
  // retrato do personagem: os mesmos campos, guardados na vaga que o mod informou (ou na que está em uso)
  if (r.count > 0) {
    const j = await prisma.jogador.findUnique({ where: { uuid: d.uuid } });
    if (j) {
      const base = { nivel: j.nivel, xp: j.xp, rank: j.rank, classe: j.classe, moedas: j.moedas, titulo: j.titulo, conquistas: j.conquistas, mortes: j.mortes,
        reputacao: j.reputacao ?? {}, campanhas: j.campanhas ?? {} };
      await prisma.personagem.updateMany({ where: d.vaga ? { uuid: d.uuid, vaga: d.vaga } : { uuid: d.uuid, emUso: true }, data: { base: base as object } }).catch(() => null);
    }
  }
  if (r.count > 0) await registrarProgresso(antes, { nivel: d.nivel, rank: d.rank, conquistas: d.conquistas }).catch(() => null);
  return NextResponse.json({ ok: r.count > 0 });
}
