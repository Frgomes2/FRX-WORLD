import { NextResponse } from "next/server";
import { servidorMc } from "@/lib/servidor-mc";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Usado pelo atalho "FRX-WORLD": liga o servidor Minecraft assim que o painel sobe. */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  if (servidorMc.estado !== "parado") return NextResponse.json({ ok: true, motivo: "já estava ligado" });
  try {
    await servidorMc.iniciar();
    return NextResponse.json({ ok: true });
  } catch (e) {
    return NextResponse.json({ ok: false, erro: (e as Error).message }, { status: 409 });
  }
}
