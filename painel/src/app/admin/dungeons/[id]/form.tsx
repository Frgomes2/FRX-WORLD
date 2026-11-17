"use client";

import Link from "next/link";
import { useActionState } from "react";
import { salvarDungeon } from "../actions";

type Valores = Record<string, string | number | boolean> & { id: string; ativa: boolean };
const campo = "mt-1 w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 outline-none focus:border-ouro";

function C({ nome, rotulo, valores, tipo = "text", passo }: { nome: string; rotulo: string; valores: Valores; tipo?: string; passo?: string }) {
  return <label className="text-sm">{rotulo}<input name={nome} type={tipo} step={passo} defaultValue={String(valores[nome] ?? "")} className={campo} /></label>;
}

export function FormDungeon({ valores }: { valores: Valores }) {
  const [estado, acao, salvando] = useActionState(salvarDungeon, null);
  return (
    <form action={acao} className="max-w-4xl space-y-5">
      <input type="hidden" name="id" value={valores.id} />
      <section className="grid gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-5 sm:grid-cols-4">
        <div className="sm:col-span-4 font-titulo text-xl text-ouro">Dungeon</div>
        <div className="sm:col-span-2"><C nome="nome" rotulo="Nome" valores={valores} /></div>
        <C nome="nivelRecomendado" rotulo="Nível recomendado" valores={valores} tipo="number" />
        <C nome="recargaHoras" rotulo="Recarga do prêmio (horas)" valores={valores} tipo="number" />
        <label className="text-sm sm:col-span-4">Descrição
          <textarea name="descricao" defaultValue={String(valores.descricao ?? "")} rows={2} className={campo} /></label>
        <C nome="distanciaMin" rotulo="Distância mínima do spawn" valores={valores} tipo="number" />
        <C nome="distanciaMax" rotulo="Distância máxima" valores={valores} tipo="number" />
      </section>
      <section className="grid gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-5 sm:grid-cols-4">
        <div className="sm:col-span-4 font-titulo text-xl text-ouro">Guardião final (camada 3)</div>
        <C nome="bossNome" rotulo="Nome" valores={valores} />
        <C nome="bossTipo" rotulo="Mob base (ID)" valores={valores} />
        <C nome="bossVida" rotulo="Vida" valores={valores} tipo="number" />
        <C nome="bossDano" rotulo="Dano por golpe" valores={valores} tipo="number" passo="0.5" />
        <C nome="bossVelocidade" rotulo="Velocidade (×)" valores={valores} tipo="number" passo="0.05" />
        <C nome="lacaioTipo" rotulo="Lacaios (ID)" valores={valores} />
        <C nome="lacaioQtd" rotulo="Quantidade de lacaios" valores={valores} tipo="number" />
      </section>
      {[1, 2].map((n) => (
        <section key={n} className="grid gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-5 sm:grid-cols-4">
          <div className="sm:col-span-4">
            <p className="font-titulo text-xl text-ouro">Guardião da camada {n}</p>
            <p className="text-xs text-pedra-500">Fica na antecâmara antes do Portão Selado. Derrotado, dá a Chave da Camada {n} a cada jogador por perto.</p>
          </div>
          <C nome={`andar${n}.bossNome`} rotulo="Nome" valores={valores} />
          <C nome={`andar${n}.bossTipo`} rotulo="Mob base (ID)" valores={valores} />
          <C nome={`andar${n}.bossVida`} rotulo="Vida" valores={valores} tipo="number" />
          <C nome={`andar${n}.bossDano`} rotulo="Dano por golpe" valores={valores} tipo="number" passo="0.5" />
          <C nome={`andar${n}.bossVelocidade`} rotulo="Velocidade (×)" valores={valores} tipo="number" passo="0.05" />
          <C nome={`andar${n}.lacaioTipo`} rotulo="Lacaios (ID)" valores={valores} />
          <C nome={`andar${n}.lacaioQtd`} rotulo="Quantidade de lacaios" valores={valores} tipo="number" />
        </section>
      ))}
      <section className="grid gap-4 rounded-xl border border-pedra-700 bg-pedra-900 p-5 sm:grid-cols-4">
        <div className="sm:col-span-4 font-titulo text-xl text-ouro">Prêmio final</div>
        <C nome="recompensaXp" rotulo="XP" valores={valores} tipo="number" />
        <label className="text-sm sm:col-span-3">Loot (item*quantidade@chance%)
          <input name="loot" defaultValue={String(valores.loot ?? "")} className={campo} placeholder="minecraft:diamond*2@50, minecraft:emerald*5" /></label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="ativa" defaultChecked={valores.ativa} className="h-4 w-4 accent-yellow-600" /> Ativa</label>
      </section>
      {estado?.erro && <p className="text-red-400">{estado.erro}</p>}
      <div className="flex gap-3">
        <button disabled={salvando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro disabled:opacity-60">{salvando ? "Salvando..." : "Salvar"}</button>
        <Link href="/admin/dungeons" className="rounded-md border border-pedra-700 px-5 py-2 hover:border-ouro">Cancelar</Link>
      </div>
    </form>
  );
}
