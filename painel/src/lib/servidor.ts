import { NextResponse } from "next/server";

/** Confere o token enviado pelo servidor Minecraft no header x-servidor-token. */
export function tokenValido(req: Request) {
  const token = req.headers.get("x-servidor-token");
  return !!process.env.SERVIDOR_TOKEN && token === process.env.SERVIDOR_TOKEN;
}

export function naoAutorizado() {
  return NextResponse.json({ erro: "token inválido" }, { status: 401 });
}
