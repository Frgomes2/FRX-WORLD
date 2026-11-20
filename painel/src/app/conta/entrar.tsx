"use client";

import { useState } from "react";

const campo = "mt-1 block w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

/** Entrar na conta do site: com nick e senha, ou (primeiro acesso / senha esquecida) com o código que o jogo mostra. */
export function EntrarConta() {
  const [modo, setModo] = useState<"entrar" | "criar">("entrar");
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function enviar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const senha = String(f.get("senha") ?? "");
    if (modo === "criar" && senha !== String(f.get("senha2") ?? "")) { setErro("As duas senhas não são iguais."); return; }
    setEnviando(true); setErro("");
    const r = await fetch("/api/publico/conta", { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ acao: modo, nick: String(f.get("nick") ?? ""), senha, codigo: String(f.get("codigo") ?? "") }) }).catch(() => null);
    if (r?.ok) { window.location.reload(); return; }
    const j = await r?.json().catch(() => null);
    setErro(j?.erro ?? "Não consegui falar com o servidor. Tente de novo.");
    setEnviando(false);
  }

  return (
    <div className="mx-auto max-w-md rounded-2xl border border-pedra-700 bg-pedra-900 p-6">
      <h1 className="font-titulo text-3xl text-ouro">Minha conta</h1>
      <p className="mb-5 text-sm text-pedra-500">Veja o inventário, as moedas e os seus personagens sem abrir o jogo.</p>
      <div className="mb-5 flex gap-2">
        {([["entrar", "Já tenho senha"], ["criar", "Primeiro acesso"]] as const).map(([k, r]) => (
          <button key={k} type="button" onClick={() => { setModo(k); setErro(""); }}
            className={`flex-1 rounded-md border px-3 py-2 text-sm ${modo === k ? "border-ouro bg-pedra-700 text-ouro" : "border-pedra-700 hover:border-ouro-escuro"}`}>{r}</button>
        ))}
      </div>
      {modo === "criar" && (
        <ol className="mb-5 list-decimal space-y-1 rounded-lg border border-pedra-700 bg-pedra-950/60 py-3 pl-8 pr-3 text-sm text-pedra-500">
          <li>Entre no jogo e aperte <b className="text-pergaminho">F9</b>.</li>
          <li>O jogo mostra um código de 6 números, que vale por 10 minutos.</li>
          <li>Digite o código aqui e escolha a sua senha.</li>
        </ol>
      )}
      <form onSubmit={enviar} className="space-y-4">
        <label className="block text-sm">Seu nick no jogo
          <input name="nick" required maxLength={16} autoComplete="username" className={campo} />
        </label>
        {modo === "criar" && (
          <label className="block text-sm">Código do jogo
            <input name="codigo" required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" className={`${campo} font-titulo text-2xl tracking-[0.4em]`} />
          </label>
        )}
        <label className="block text-sm">{modo === "criar" ? "Escolha uma senha (8 caracteres ou mais)" : "Senha"}
          <input name="senha" type="password" required minLength={modo === "criar" ? 8 : 1} maxLength={72} autoComplete={modo === "criar" ? "new-password" : "current-password"} className={campo} />
        </label>
        {modo === "criar" && (
          <label className="block text-sm">Repita a senha
            <input name="senha2" type="password" required maxLength={72} autoComplete="new-password" className={campo} />
          </label>
        )}
        {erro && <p className="rounded-md border border-red-500/50 bg-red-500/10 px-3 py-2 text-sm text-red-300">{erro}</p>}
        <button disabled={enviando} className="w-full rounded-md bg-ouro px-4 py-2.5 font-semibold text-pedra-950 hover:brightness-110 disabled:opacity-50">
          {enviando ? "Entrando…" : modo === "criar" ? "Criar senha e entrar" : "Entrar"}
        </button>
      </form>
      <p className="mt-4 text-center text-xs text-pedra-500">
        {modo === "entrar" ? <>Esqueceu a senha? Use <button type="button" onClick={() => setModo("criar")} className="text-ouro hover:underline">Primeiro acesso</button> para criar outra com um código novo.</> : "A senha é só do site: não é a senha da sua conta do Minecraft."}
      </p>
    </div>
  );
}

export function BotaoSair() {
  return (
    <button type="button" className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro"
      onClick={async () => { await fetch("/api/publico/conta", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ acao: "sair" }) }).catch(() => null); window.location.href = "/"; }}>
      Sair
    </button>
  );
}
