import { NextResponse } from "next/server";
import { tokenValido, naoAutorizado } from "@/lib/servidor";
import { guardarStatus, type StatusJogo } from "@/lib/status-servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** O mod manda aqui o retrato do servidor a cada 10 s. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  const corpo = (await req.json().catch(() => null)) as StatusJogo | null;
  if (!corpo || typeof corpo !== "object") return NextResponse.json({ erro: "corpo inválido" }, { status: 400 });
  guardarStatus(corpo);
  return NextResponse.json({ ok: true });
}
