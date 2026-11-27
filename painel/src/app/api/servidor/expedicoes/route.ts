import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

const ms = z.number().int().min(0);
const schema = z.object({
  expedicao: z.object({
    numero: z.number().int().min(1),
    inicio: ms,
    proxima: ms,
    fim: ms.optional(),
    tamanho: z.number().int().min(1),
    tipo: z.string().max(30).optional(),
    ativa: z.boolean().optional(),
    versao: z.string().max(30).optional(),
    estruturas: z.number().int().min(0).optional(),
    agora: z.array(z.string().max(40)).max(200).optional(),
  }),
  participantes: z.array(z.object({
    uuid: z.string().min(1).max(64),
    nick: z.string().min(1).max(40),
    primeira: ms,
    ultima: ms,
    tempoSeg: z.number().int().min(0),
    chunks: z.number().int().min(0),
    mortes: z.number().int().min(0),
    chefes: z.number().int().min(0),
    estruturas: z.number().int().min(0),
    tesouros: z.number().int().min(0),
  })).max(500),
});

/** O servidor manda a expedição atual (a cada minuto com gente lá) e o fechamento da anterior na troca. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  const { expedicao: e, participantes } = dados.data;
  const campos = {
    proxima: new Date(e.proxima),
    fim: e.fim ? new Date(e.fim) : null,
    tamanho: e.tamanho,
    ativa: e.ativa ?? true,
    ...(e.versao ? { versao: e.versao } : {}),
    ...(e.estruturas !== undefined ? { estruturas: e.estruturas } : {}),
    agora: e.fim ? [] : e.agora ?? [],
  };
  if (!e.fim) {
    // números acima da atual não existem mais (a contagem foi zerada no servidor): somem do site
    await prisma.expedicao.deleteMany({ where: { numero: { gt: e.numero } } });
    // mesmo número com outro início = expedição nova reaproveitando o número: as fichas antigas saem
    if (e.inicio) {
      const atual = await prisma.expedicao.findUnique({ where: { numero: e.numero }, select: { inicio: true } });
      if (atual && Math.abs(atual.inicio.getTime() - e.inicio) > 60_000) await prisma.participacaoExpedicao.deleteMany({ where: { numero: e.numero } });
    }
  }
  await prisma.expedicao.upsert({
    where: { numero: e.numero },
    create: { numero: e.numero, inicio: new Date(e.inicio || Date.now()), tipo: e.tipo ?? "normal", ...campos },
    update: { ...(e.inicio ? { inicio: new Date(e.inicio) } : {}), ...campos },
  });
  // a expedição nova encerra qualquer outra que tenha ficado aberta (servidor caiu no meio da troca, por exemplo)
  if (!e.fim) await prisma.expedicao.updateMany({ where: { numero: { lt: e.numero }, fim: null }, data: { fim: new Date(e.inicio || Date.now()), agora: [] } });
  for (const p of participantes) {
    const v = { nick: p.nick, primeira: new Date(p.primeira), ultima: new Date(p.ultima), tempoSeg: p.tempoSeg, chunks: p.chunks, mortes: p.mortes, chefes: p.chefes, estruturas: p.estruturas, tesouros: p.tesouros };
    await prisma.participacaoExpedicao.upsert({ where: { numero_uuid: { numero: e.numero, uuid: p.uuid } }, create: { numero: e.numero, uuid: p.uuid, ...v }, update: v });
  }
  return NextResponse.json({ ok: true });
}
