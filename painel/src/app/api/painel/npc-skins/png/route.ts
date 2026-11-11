import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  if (!(await auth())?.user) return new NextResponse(null, { status: 401 });
  const chave = new URL(req.url).searchParams.get("chave") ?? "";
  const s = await prisma.npcSkin.findUnique({ where: { chave } });
  if (!s) return new NextResponse(null, { status: 404 });
  return new NextResponse(new Uint8Array(s.png), { headers: { "content-type": "image/png", "x-skin-modelo": s.slim ? "slim" : "classic", "cache-control": "no-store" } });
}
