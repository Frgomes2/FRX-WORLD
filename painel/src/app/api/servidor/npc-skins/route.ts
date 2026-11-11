import crypto from "crypto";
import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { tokenValido, naoAutorizado } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod busca as skins de NPC. ?versao=X → {igual:true} se nada mudou. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.npcSkin.findMany({ select: { chave: true, atualizadoEm: true, slim: true }, orderBy: { chave: "asc" } });
  const versao = crypto.createHash("sha1").update(lista.map((s) => `${s.chave}@${s.atualizadoEm.getTime()}@${s.slim}`).join("|")).digest("hex").slice(0, 16);
  if (new URL(req.url).searchParams.get("versao") === versao) return NextResponse.json({ igual: true });
  const skins = await prisma.npcSkin.findMany({ orderBy: { chave: "asc" } });
  return NextResponse.json({ versao, skins: skins.map((s) => ({ chave: s.chave, slim: s.slim, png: Buffer.from(s.png).toString("base64") })) });
}
