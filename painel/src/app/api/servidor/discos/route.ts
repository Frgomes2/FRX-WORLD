import { NextResponse } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { naoAutorizado, tokenValido } from "@/lib/servidor";
import { temArquivo } from "@/lib/discos";
import { BASE_DISCO, pngDaTextura } from "@/lib/codex";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O servidor lê os discos à venda ao ligar e a cada minuto. Só entram os ativos que têm o arquivo de áudio. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.disco.findMany({ where: { ativo: true }, orderBy: [{ ordem: "asc" }, { nome: "asc" }] });
  return NextResponse.json({
    discos: lista.filter((d) => temArquivo(d.codigo)).map((d) => ({
      codigo: d.codigo, nome: d.nome, artista: d.artista, estilo: d.estilo, descricao: d.descricao, preco: d.preco, rankMin: d.rankMin, limite: d.limite, arte: pngDaTextura(d.textura) ? BASE_DISCO + d.modelo : d.arte, duracao: d.duracao,
    })),
  });
}

const estoque = z.object({ estoque: z.record(z.string().max(40), z.number().int().min(0)) });

/** O servidor avisa quantas unidades de cada disco já foram vendidas (para o limite de unidades no mundo). */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = estoque.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  for (const [codigo, emitidas] of Object.entries(dados.data.estoque)) await prisma.disco.updateMany({ where: { codigo }, data: { emitidas } });
  return NextResponse.json({ ok: true });
}
