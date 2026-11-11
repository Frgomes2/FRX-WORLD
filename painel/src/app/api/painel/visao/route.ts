import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { servidorMc } from "@/lib/servidor-mc";
import { statusAtual } from "@/lib/status-servidor";
import { contarMods, feed, mapaExplorado } from "@/lib/mapa-mundo";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Dados ao vivo da Visão geral. ?mapa=1 inclui o mapa explorado (mais pesado). */
export async function GET(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const comMapa = new URL(req.url).searchParams.get("mapa") === "1";
  const resumo = servidorMc.resumo();
  let mapa = null;
  if (comMapa) { try { mapa = mapaExplorado(); } catch { mapa = null; } }
  return NextResponse.json({
    agora: Date.now(),
    servidor: resumo.tipo === "estado" ? resumo : null,
    status: statusAtual(),
    mods: contarMods(),
    feed: feed(servidorMc.linhas),
    mapa,
  });
}
