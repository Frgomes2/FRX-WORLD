import { NextResponse } from "next/server";
import { z } from "zod";
import { guardarCodigo } from "@/lib/conta-site";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

const schema = z.object({ uuid: z.string().min(1).max(40), codigo: z.string().regex(/^\d{6}$/), minutos: z.number().int().min(1).max(30) });

/** O mod avisa o código de 6 dígitos que acabou de mostrar ao jogador (tela "Conta do site", tecla F9). */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const d = schema.safeParse(await req.json().catch(() => null));
  if (!d.success) return NextResponse.json({ erro: "dados inválidos" }, { status: 400 });
  await guardarCodigo(d.data.uuid, d.data.codigo, d.data.minutos);
  return NextResponse.json({ ok: true });
}
