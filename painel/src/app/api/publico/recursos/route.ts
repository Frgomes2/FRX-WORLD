import { NextResponse } from "next/server";
import { pacoteRecursos } from "@/lib/codex";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Pacote de recursos com a aparência das armas do Códex. O jogo de cada jogador baixa daqui ao entrar no servidor. */
export async function GET() {
  try {
    const p = await pacoteRecursos();
    return new NextResponse(new Uint8Array(p.zip), {
      headers: { "content-type": "application/zip", "content-disposition": 'attachment; filename="frx-world-codex.zip"', "cache-control": "public, max-age=300", etag: `"${p.sha1}"` },
    });
  } catch {
    return new NextResponse(null, { status: 503 });
  }
}
