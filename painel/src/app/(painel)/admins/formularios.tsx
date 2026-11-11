"use client";

import { useActionState } from "react";
import { criarAdmin, trocarSenha, type Resultado } from "./actions";

const campo = "w-full rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 outline-none focus:border-ouro";
const botao = "rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-50";

function Aviso({ r }: { r: Resultado }) {
  if (r.erro) return <p className="text-sm text-red-400">{r.erro}</p>;
  if (r.ok) return <p className="text-sm text-green-400">{r.ok}</p>;
  return null;
}

export function NovoAdmin() {
  const [r, acao, enviando] = useActionState(criarAdmin, {});
  return (
    <form action={acao} className="flex flex-col gap-3 rounded-xl border border-pedra-700 bg-pedra-900/60 p-5">
      <h2 className="font-titulo text-xl">Adicionar admin</h2>
      <input name="nome" placeholder="Nome" className={campo} />
      <input name="email" type="email" placeholder="E-mail (é o login)" className={campo} />
      <input name="senha" type="password" placeholder="Senha inicial (10+ caracteres, letras e números)" className={campo} autoComplete="new-password" />
      <Aviso r={r} />
      <button disabled={enviando} className={botao}>{enviando ? "Salvando..." : "Adicionar"}</button>
    </form>
  );
}

export function MinhaSenha() {
  const [r, acao, enviando] = useActionState(trocarSenha, {});
  return (
    <form action={acao} className="flex flex-col gap-3 rounded-xl border border-pedra-700 bg-pedra-900/60 p-5">
      <h2 className="font-titulo text-xl">Minha senha</h2>
      <input name="atual" type="password" placeholder="Senha atual" className={campo} autoComplete="current-password" />
      <input name="nova" type="password" placeholder="Nova senha" className={campo} autoComplete="new-password" />
      <Aviso r={r} />
      <button disabled={enviando} className={botao}>{enviando ? "Salvando..." : "Trocar senha"}</button>
    </form>
  );
}
