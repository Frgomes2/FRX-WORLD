import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";
import { BASE_MODELO, pacoteRecursos, pngDaTextura, urlPublica } from "@/lib/codex";

export const dynamic = "force-dynamic";

/** O servidor lê as armas do Códex ao ligar e a cada minuto, junto com o endereço do pacote de recursos. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.armaCodex.findMany({ where: { ativa: true }, orderBy: [{ rank: "asc" }, { nome: "asc" }] });
  const pacote = await pacoteRecursos().catch(() => null);
  return NextResponse.json({
    armas: lista.map((a) => ({
      codigo: a.codigo, nome: a.nome, descricao: a.descricao, itemBase: a.itemBase, rank: a.rank, raridade: a.raridade, dano: a.dano, limite: a.limite,
      ferreiro: a.ferreiro, preco: a.preco, nivelMin: a.nivelMin, evolui: a.evolui, danoPorNivel: a.danoPorNivel, umaPorJogador: a.umaPorJogador, inquebravel: (a as typeof a & { inquebravel?: boolean }).inquebravel ?? false, nivelUso: (a as typeof a & { nivelUso?: boolean }).nivelUso ?? false,
      encantos: a.encantos.split(",").map((e) => e.trim()).filter(Boolean),
      modelo: pngDaTextura(a.textura) ? BASE_MODELO + a.modelo : 0,
    })),
    pacote: pacote && (pacote.armas > 0 || pacote.discos > 0) ? { url: `${urlPublica()}/api/publico/recursos?v=${pacote.sha1.slice(0, 12)}`, sha1: pacote.sha1 } : null,
  });
}

const schema = z.object({
  estoque: z.record(z.string().max(60), z.object({
    emitidas: z.number().int().min(0), donos: z.record(z.string(), z.string()).optional(),
    primeiro: z.object({ nome: z.string().max(60), quando: z.number() }).optional(),
    nivelUso: z.number().int().min(0).max(100).optional(),
  })),
});

/** O servidor avisa quantas unidades de cada arma já existem no mundo e com quem estão. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  // primeiroNome/primeiroEm entram por cast até o Prisma Client ser gerado de novo (o início do servidor faz isso)
  const tabela = prisma.armaCodex as unknown as { updateMany: (a: { where: { codigo: string }; data: Record<string, unknown> }) => Promise<unknown> };
  for (const [codigo, e] of Object.entries(dados.data.estoque)) {
    const data: Record<string, unknown> = { emitidas: e.emitidas, donos: e.donos ?? {} };
    if (e.nivelUso !== undefined) data.nivelUsoMax = e.nivelUso;
    if (e.primeiro) { data.primeiroNome = e.primeiro.nome; data.primeiroEm = e.primeiro.quando > 0 ? new Date(e.primeiro.quando) : null; }
    await tabela.updateMany({ where: { codigo }, data });
  }
  return NextResponse.json({ ok: true });
}
