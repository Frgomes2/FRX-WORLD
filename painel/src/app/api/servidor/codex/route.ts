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
      ferreiro: a.ferreiro, preco: a.preco, nivelMin: a.nivelMin, evolui: a.evolui, danoPorNivel: a.danoPorNivel, umaPorJogador: a.umaPorJogador,
      encantos: a.encantos.split(",").map((e) => e.trim()).filter(Boolean),
      modelo: pngDaTextura(a.textura) ? BASE_MODELO + a.modelo : 0,
    })),
    pacote: pacote && (pacote.armas > 0 || pacote.discos > 0) ? { url: `${urlPublica()}/api/publico/recursos?v=${pacote.sha1.slice(0, 12)}`, sha1: pacote.sha1 } : null,
  });
}

const schema = z.object({
  estoque: z.record(z.string().max(60), z.object({ emitidas: z.number().int().min(0), donos: z.record(z.string(), z.string()).optional() })),
});

/** O servidor avisa quantas unidades de cada arma já existem no mundo e com quem estão. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const dados = schema.safeParse(await req.json().catch(() => null));
  if (!dados.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  for (const [codigo, e] of Object.entries(dados.data.estoque))
    await prisma.armaCodex.updateMany({ where: { codigo }, data: { emitidas: e.emitidas, donos: e.donos ?? {} } });
  return NextResponse.json({ ok: true });
}
