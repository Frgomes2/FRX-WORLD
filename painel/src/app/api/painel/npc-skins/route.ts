import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { CHAVE_VALIDA } from "@/lib/npcs";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const lista = await prisma.npcSkin.findMany({ select: { chave: true, slim: true, atualizadoEm: true } });
  return NextResponse.json(lista.map((s) => ({ ...s, atualizadoEm: s.atualizadoEm.getTime() })));
}

/** { chave, slim, png: "data:image/png;base64,..." } — PNG 64×64. */
export async function POST(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const { chave, slim, png } = ((await req.json().catch(() => ({}))) ?? {}) as { chave?: string; slim?: boolean; png?: string };
  if (!chave || !CHAVE_VALIDA.test(chave)) return NextResponse.json({ erro: "Tipo de NPC inválido." }, { status: 400 });
  const b64 = String(png ?? "").replace(/^data:image\/png;base64,/, "");
  const buf = Buffer.from(b64, "base64");
  const ehPng = buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47 && buf.toString("ascii", 12, 16) === "IHDR";
  if (!ehPng) return NextResponse.json({ erro: "O arquivo não é um PNG." }, { status: 400 });
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  if (w !== 64 || h !== 64) return NextResponse.json({ erro: `A skin precisa ter 64×64 pixels (esta tem ${w}×${h}).` }, { status: 400 });
  if (buf.length > 150_000) return NextResponse.json({ erro: "Arquivo grande demais." }, { status: 400 });
  await prisma.npcSkin.upsert({ where: { chave }, create: { chave, slim: !!slim, png: buf }, update: { slim: !!slim, png: buf } });
  return NextResponse.json({ ok: true });
}

export async function DELETE(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const chave = new URL(req.url).searchParams.get("chave") ?? "";
  if (!CHAVE_VALIDA.test(chave)) return NextResponse.json({ erro: "Tipo de NPC inválido." }, { status: 400 });
  await prisma.npcSkin.deleteMany({ where: { chave } });
  return NextResponse.json({ ok: true });
}
