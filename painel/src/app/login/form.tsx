"use client";

import { useActionState } from "react";
import { entrar } from "./actions";

export function FormLogin() {
  const [erro, acao, enviando] = useActionState(entrar, null);
  return (
    <form action={acao} className="space-y-4">
      <label className="block">
        <span className="text-sm text-pedra-500">E-mail</span>
        <input name="email" type="email" required autoComplete="username"
          className="mt-1 w-full rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 outline-none focus:border-ouro" />
      </label>
      <label className="block">
        <span className="text-sm text-pedra-500">Senha</span>
        <input name="senha" type="password" required autoComplete="current-password"
          className="mt-1 w-full rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 outline-none focus:border-ouro" />
      </label>
      {erro && <p className="text-sm text-red-400">{erro}</p>}
      <button disabled={enviando}
        className="w-full rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-60">
        {enviando ? "Entrando..." : "Entrar"}
      </button>
    </form>
  );
}
