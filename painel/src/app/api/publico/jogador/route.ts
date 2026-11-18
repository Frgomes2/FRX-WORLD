import { NextResponse } from "next/server";
import { perfilPublico } from "@/lib/publico";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Ficha pública de um jogador: ?nick=Fulano */
export async function GET(req: Request) {
  const url = new URL(req.url);
  const nick = url.searchParams.get("nick") ?? "";
  const vaga = Math.max(0, Math.min(3, Number(url.searchParams.get("vaga") ?? 0) || 0));
  try {
    const perfil = await perfilPublico(nick, vaga);
    if (!perfil) return NextResponse.json({ erro: "jogador não encontrado" }, { status: 404 });
    return NextResponse.json(perfil, { headers: { "cache-control": "public, max-age=20" } });
  } catch {
    return NextResponse.json({ erro: "indisponível" }, { status: 503 });
  }
}
