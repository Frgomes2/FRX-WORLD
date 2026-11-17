import { NextResponse } from "next/server";
import { dadosPublicos } from "@/lib/publico";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Dados do site público (sem login). Só o que lib/publico.ts libera. */
export async function GET() {
  try {
    return NextResponse.json(await dadosPublicos(), { headers: { "cache-control": "public, max-age=10" } });
  } catch {
    return NextResponse.json({ erro: "indisponível" }, { status: 503 });
  }
}
