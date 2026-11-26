import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { reiniciarSoPainel, servidorMc } from "@/lib/servidor-mc";
import { novoMundo } from "@/lib/mundo";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  return NextResponse.json(servidorMc.resumo());
}

/** { acao: "iniciar" | "parar" | "reiniciar" | "reiniciarPainel" | "comando", comando?: string } */
export async function POST(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const { acao, comando, seed } = (await req.json().catch(() => ({}))) as { acao?: string; comando?: string; seed?: string };

  try {
    switch (acao) {
      case "iniciar":
        await servidorMc.iniciar();
        break;
      case "parar":
        servidorMc.parar();
        break;
      case "reiniciar":
        await servidorMc.reiniciar();
        break;
      case "novoMundo": {
        const r = novoMundo(seed);
        return NextResponse.json({ ok: true, ...r, ...servidorMc.resumo() });
      }
      case "reiniciarPainel":
        reiniciarSoPainel();
        return NextResponse.json({ ok: true, ...servidorMc.resumo() });
      case "comando":
        servidorMc.comando(String(comando ?? ""));
        break;
      default:
        return NextResponse.json({ erro: "ação desconhecida" }, { status: 400 });
    }
    return NextResponse.json({ ok: true, ...servidorMc.resumo() });
  } catch (e) {
    return NextResponse.json({ erro: (e as Error).message }, { status: 409 });
  }
}
