import { NextResponse } from "next/server";
import { skinPublica } from "@/lib/publico";
import { textura } from "@/lib/jar-icones";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Skin (PNG 64x64) que o jogador usa no servidor: ?nick=Fulano. Sem skin própria, a do Steve. */
export async function GET(req: Request) {
  const nick = new URL(req.url).searchParams.get("nick") ?? "";
  let png: Uint8Array | null = null;
  try { png = await skinPublica(nick); } catch { /* cai no Steve */ }
  if (!png) {
    const steve = textura("minecraft/textures/entity/player/wide/steve.png");
    if (!steve) return new NextResponse(null, { status: 404 });
    png = new Uint8Array(steve);
  }
  return new NextResponse(png as unknown as BodyInit, { headers: { "content-type": "image/png", "cache-control": "public, max-age=1800" } });
}
