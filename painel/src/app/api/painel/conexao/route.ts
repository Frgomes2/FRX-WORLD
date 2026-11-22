import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { conexoes, statusAtual, usoCpu } from "@/lib/status-servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Teste de conexão do admin: ping de cada jogador e saúde do servidor. ?desde=<ms> limita as amostras. */
export async function GET(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const agora = Date.now();
  const pedido = Number(new URL(req.url).searchParams.get("desde"));
  const desde = Number.isFinite(pedido) && pedido > 0 ? pedido : agora - 30 * 60_000;
  const st = statusAtual();
  const recentes = st.historico.filter((h) => h.t >= desde);
  const mspt = recentes.map((h) => h.mspt ?? 0);
  return NextResponse.json({
    agora,
    ligado: !!st.dados,
    servidor: st.dados ? {
      tps: st.dados.tps, mspt: Math.round(st.dados.mspt * 10) / 10,
      msptMedio: mspt.length ? Math.round((mspt.reduce((a, b) => a + b, 0) / mspt.length) * 10) / 10 : null,
      msptPico: mspt.length ? Math.round(Math.max(...mspt) * 10) / 10 : null,
      memPct: st.dados.memMaxMb > 0 ? Math.round((st.dados.memUsadaMb / st.dados.memMaxMb) * 100) : null,
      cpu: usoCpu(),
    } : null,
    jogadores: conexoes(desde),
  });
}
