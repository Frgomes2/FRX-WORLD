import { NextResponse } from "next/server";
import { paginaExpedicoes } from "@/lib/expedicoes";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Expedição atual, histórico e números gerais. `agora` é o relógio do servidor (a contagem regressiva acerta o do navegador por ele). */
export async function GET() {
  try {
    return NextResponse.json(await paginaExpedicoes(), { headers: { "cache-control": "no-store" } });
  } catch {
    return NextResponse.json({ erro: "indisponível" }, { status: 503 });
  }
}
