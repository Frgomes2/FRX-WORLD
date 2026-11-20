import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { contaLogada } from "@/lib/conta-site";
import { iconeItem } from "@/lib/jar-icones";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** ?id=minecraft:iron_sword → PNG tirado do jar do jogo ou do mod. */
export async function GET(req: Request) {
  if (!(await auth())?.user && !(await contaLogada())) return new NextResponse(null, { status: 401 });
  const id = new URL(req.url).searchParams.get("id") ?? "";
  if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id)) return new NextResponse(null, { status: 400 });
  const png = iconeItem(id);
  if (!png) return new NextResponse(null, { status: 404, headers: { "cache-control": "private, max-age=600" } });
  return new NextResponse(new Uint8Array(png), { headers: { "content-type": "image/png", "cache-control": "private, max-age=86400" } });
}
