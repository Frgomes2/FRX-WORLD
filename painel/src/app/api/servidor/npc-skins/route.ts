import crypto from "crypto";
import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { tokenValido, naoAutorizado } from "@/lib/servidor";
import { chaveSkin, pngDaSkin } from "@/lib/criaturas-codex";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod busca as skins de NPC e as das criaturas do Códex. ?versao=X → {igual:true} se nada mudou. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const lista = await prisma.npcSkin.findMany({ select: { chave: true, atualizadoEm: true, slim: true }, orderBy: { chave: "asc" } });
  const bichos = await prisma.criaturaCodex.findMany({ where: { ativa: true, NOT: { skin: "" } }, select: { codigo: true, atualizadoEm: true, slim: true }, orderBy: { codigo: "asc" } });
  const versao = crypto.createHash("sha1")
    .update([...lista.map((s) => `${s.chave}@${s.atualizadoEm.getTime()}@${s.slim}`), ...bichos.map((b) => `${chaveSkin(b.codigo)}@${b.atualizadoEm.getTime()}@${b.slim}`)].join("|"))
    .digest("hex").slice(0, 16);
  if (new URL(req.url).searchParams.get("versao") === versao) return NextResponse.json({ igual: true });
  const skins = (await prisma.npcSkin.findMany({ orderBy: { chave: "asc" } })).map((s) => ({ chave: s.chave, slim: s.slim, png: Buffer.from(s.png).toString("base64") }));
  for (const b of await prisma.criaturaCodex.findMany({ where: { ativa: true, NOT: { skin: "" } }, select: { codigo: true, slim: true, skin: true } }))
    if (pngDaSkin(b.skin)) skins.push({ chave: chaveSkin(b.codigo), slim: b.slim, png: b.skin });
  return NextResponse.json({ versao, skins });
}
