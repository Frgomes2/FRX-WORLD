import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { listaCriaturas } from "@/lib/entidades";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  return NextResponse.json({ criaturas: await listaCriaturas() });
}
