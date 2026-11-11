import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { monitorar } from "@/lib/monitor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  try {
    return NextResponse.json(monitorar());
  } catch (e) {
    return NextResponse.json({ erro: e instanceof Error ? e.message : "falhou" }, { status: 500 });
  }
}
