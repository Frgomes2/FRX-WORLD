import { NextResponse } from "next/server";
import { lerConfig } from "@/lib/config-jogo";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const dynamic = "force-dynamic";

/** O servidor Minecraft lê a configuração de jogo ao ligar e a cada minuto. */
export async function GET(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  return NextResponse.json(await lerConfig());
}
