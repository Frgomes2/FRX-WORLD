import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { contaLogada } from "@/lib/conta-site";
import { listaItens } from "@/lib/itens";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await auth())?.user && !(await contaLogada())) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  return NextResponse.json({ itens: await listaItens() });
}
