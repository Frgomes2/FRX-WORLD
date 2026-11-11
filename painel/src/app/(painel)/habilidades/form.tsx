"use client";

import { useActionState } from "react";
import { salvarHabilidade, type Estado } from "./actions";
import { CLASSES, ELEMENTOS, TIPOS } from "@/lib/habilidades";

const campo = "w-full rounded border border-pedra-700 bg-pedra-950 px-2 py-1 text-sm";
const rotulo = "mb-1 block text-xs text-pedra-500";

export type HabForm = {
  id?: string; codigo: string; nome: string; descricao: string; classe: string; tipo: string; elemento: string; icone: string;
  custo: number; cooldown: number; dano: number; alcance: number; raio: number; duracao: number; efeito: string; potencia: number;
  nivelReq: number; ultimate: boolean; aprendizado: string; ativa: boolean;
};

const num = (nome: keyof HabForm, r: string, v: number, passo = "1", dica?: string) => (
  <div className="md:col-span-1"><span className={rotulo}>{r}</span><input name={nome} type="number" step={passo} defaultValue={v} className={campo} title={dica} /></div>
);

export function FormHabilidade({ h }: { h: HabForm }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarHabilidade, null);
  return (
    <form action={acao} className={`grid gap-2 md:grid-cols-12 ${h.ativa ? "" : "opacity-50"}`}>
      {h.id && <input type="hidden" name="id" value={h.id} />}
      <div className="md:col-span-2"><span className={rotulo}>Código</span><input name="codigo" defaultValue={h.codigo} readOnly={!!h.id} className={`${campo} font-mono text-xs`} /></div>
      <div className="md:col-span-3"><span className={rotulo}>Nome</span><input name="nome" defaultValue={h.nome} className={campo} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Classe</span>
        <select name="classe" defaultValue={h.classe} className={campo}>{Object.entries(CLASSES).map(([k, v]) => <option key={k} value={k}>{v.nome}</option>)}</select></div>
      <div className="md:col-span-2"><span className={rotulo}>Tipo</span>
        <select name="tipo" defaultValue={h.tipo} className={campo} title={TIPOS[h.tipo as keyof typeof TIPOS]}>{Object.keys(TIPOS).map((k) => <option key={k} value={k}>{k}</option>)}</select></div>
      <div className="md:col-span-2"><span className={rotulo}>Elemento</span>
        <select name="elemento" defaultValue={h.elemento} className={campo}>{Object.entries(ELEMENTOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      <div className="md:col-span-1"><span className={rotulo}>Nível</span><input name="nivelReq" type="number" defaultValue={h.nivelReq} className={campo} /></div>

      <div className="md:col-span-5"><span className={rotulo}>Descrição</span><input name="descricao" defaultValue={h.descricao} className={campo} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Ícone (item)</span><input name="icone" defaultValue={h.icone} className={`${campo} font-mono text-xs`} /></div>
      <div className="md:col-span-3"><span className={rotulo}>Efeito / mob invocado</span><input name="efeito" defaultValue={h.efeito} placeholder="minecraft:speed" className={`${campo} font-mono text-xs`} /></div>
      <div className="md:col-span-2"><span className={rotulo}>Aprende por</span>
        <select name="aprendizado" defaultValue={h.aprendizado} className={campo}><option value="NIVEL">Nível</option><option value="ITEM">Tomo Arcano</option></select></div>

      {num("custo", "Custo", h.custo)}
      {num("cooldown", "Recarga (s)", h.cooldown, "0.5")}
      {num("dano", "Dano", h.dano, "0.5", "Negativo = cura")}
      {num("alcance", "Alcance", h.alcance, "0.5")}
      {num("raio", "Raio", h.raio, "0.5")}
      {num("duracao", "Duração (s)", h.duracao, "0.5")}
      {num("potencia", "Potência / qtd", h.potencia)}
      <div className="flex items-end gap-3 text-xs md:col-span-2">
        <label className="flex items-center gap-1"><input type="checkbox" name="ultimate" defaultChecked={h.ultimate} /> ultimate</label>
        <label className="flex items-center gap-1"><input type="checkbox" name="ativa" defaultChecked={h.ativa} /> ativa</label>
      </div>
      <div className="flex items-end gap-2 md:col-span-3">
        <button disabled={enviando} className="rounded bg-ouro px-3 py-1.5 text-sm font-semibold text-pedra-950 hover:bg-ouro-escuro">{h.id ? "Salvar" : "Adicionar"}</button>
        {estado && <span className={`text-xs ${estado.erro ? "text-red-400" : "text-green-400"}`}>{estado.erro ?? estado.ok}</span>}
      </div>
    </form>
  );
}
