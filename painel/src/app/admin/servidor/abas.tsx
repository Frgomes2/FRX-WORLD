"use client";

import { useState } from "react";
import { Console } from "./console";
import { Monitoramento } from "./monitoramento";
import { Conexao } from "./conexao";

export function AbasServidor() {
  const [aba, setAba] = useState<"console" | "monitor" | "conexao">("console");
  const botao = (id: "console" | "monitor" | "conexao", rot: string) => (
    <button onClick={() => setAba(id)}
      className={`rounded-lg border px-4 py-1.5 text-sm ${aba === id ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`}>{rot}</button>
  );
  return (
    <>
      <div className="mb-4 flex gap-2">{botao("console", "🖥️ Console")}{botao("monitor", "📊 Monitoramento e erros")}{botao("conexao", "📶 Teste de conexão")}</div>
      {/* o console fica montado para não perder as linhas ao trocar de aba */}
      <div className={aba === "console" ? "" : "hidden"}><Console /></div>
      {aba === "monitor" && <Monitoramento />}
      {aba === "conexao" && <Conexao />}
    </>
  );
}
