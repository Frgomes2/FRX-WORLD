import { NextResponse } from "next/server";
import { perfilPublico } from "@/lib/publico";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Ficha pública de um jogador: ?nick=Fulano */
export async function GET(req: Request) {
  const nick = new URL(req.url).searchParams.get("nick") ?? "";
  try {
    const perfil = await perfilPublico(nick);
    if (!perfil) return NextResponse.json({ erro: "jogador não encontrado" }, { status: 404 });
    return NextResponse.json(perfil, { headers: { "cache-control": "public, max-age=20" } });
  } catch {
    return NextResponse.json({ erro: "indisponível" }, { status: 503 });
  }
}
