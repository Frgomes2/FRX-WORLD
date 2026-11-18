import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";
import { pngDaSkin } from "@/lib/criaturas-codex";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Skin (PNG 64×64) de uma criatura do Códex para o bestiário do site: só as ativas e já derrotadas por alguém. */
export async function GET(req: Request) {
  const codigo = new URL(req.url).searchParams.get("c") ?? "";
  if (!/^[a-z0-9_]{2,30}$/.test(codigo)) return new NextResponse(null, { status: 404 });
  const c = await prisma.criaturaCodex.findFirst({ where: { codigo, ativa: true, derrotas: { gt: 0 } }, select: { skin: true } });
  const png = c ? pngDaSkin(c.skin) : null;
  if (!png) return new NextResponse(null, { status: 404 });
  return new NextResponse(new Uint8Array(png) as unknown as BodyInit, { headers: { "content-type": "image/png", "cache-control": "public, max-age=300" } });
}
