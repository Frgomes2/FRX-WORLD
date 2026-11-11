import { NextResponse } from "next/server";
import { servidorMc } from "@/lib/servidor-mc";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Usado pelo atalho "Desligar FRX-WORLD": para o servidor com segurança (salvando o mundo)
 * e só responde quando ele terminou de fechar.
 */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  if (servidorMc.estado === "parado") return NextResponse.json({ ok: true, motivo: "já estava parado" });

  const fechou = new Promise<void>((resolve) => {
    const ouvir = () => {
      if (servidorMc.estado === "parado") {
        servidorMc.off("evento", ouvir);
        resolve();
      }
    };
    servidorMc.on("evento", ouvir);
  });

  try {
    servidorMc.parar();
  } catch {
    /* já estava parando */
  }
  await Promise.race([fechou, new Promise((r) => setTimeout(r, 100_000))]);
  const estado: string = servidorMc.estado;
  return NextResponse.json({ ok: estado === "parado" });
}
