"use client";

import { useActionState, useState } from "react";
import { CampoItem, CampoMoedas, IconeItem, Moedas } from "@/components/itens";
import { excluirArma, salvarArma, zerarContagem, type Estado } from "./actions";
import { EditorPixel } from "./pixel";

const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const RARIDADES: [string, string][] = [["Comum", "#e9dfc8"], ["Incomum", "#5bc25b"], ["Raro", "#4aa3ff"], ["Épico", "#b085f5"], ["Lendário", "#f5d04a"]];

export type Arma = {
  id: string; codigo: string; nome: string; descricao: string; itemBase: string; rank: number; raridade: number; dano: number; limite: number;
  ferreiro: boolean; preco: number; nivelMin: number; evolui: boolean; danoPorNivel: number; umaPorJogador: boolean; inquebravel: boolean; nivelUso: boolean; encantos: string;
  textura: string; emitidas: number; ativa: boolean; donos: string[];
};
const NOVA: Arma = {
  id: "", codigo: "", nome: "", descricao: "", itemBase: "minecraft:iron_sword", rank: 1, raridade: 2, dano: 2, limite: 5, ferreiro: true, preco: 5000,
  nivelMin: 10, evolui: false, danoPorNivel: 0.1, umaPorJogador: true, inquebravel: false, nivelUso: false, encantos: "", textura: "", emitidas: 0, ativa: true, donos: [],
};

function Aparencia({ a, tam }: { a: Pick<Arma, "textura" | "itemBase">; tam: number }) {
  // eslint-disable-next-line @next/next/no-img-element
  return a.textura ? <img src={`data:image/png;base64,${a.textura}`} alt="" width={tam} height={tam} style={{ imageRendering: "pixelated" }} /> : <IconeItem id={a.itemBase} tam={tam} />;
}

function Editor({ inicial, fechar }: { inicial: Arma; fechar: () => void }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarArma, null);
  const [a, setA] = useState(inicial);
  const mudar = <K extends keyof Arma>(k: K, v: Arma[K]) => setA((x) => ({ ...x, [k]: v }));
  const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 text-sm outline-none focus:border-ouro";
  const rotulo = "mb-1 block text-xs uppercase tracking-wider text-pedra-500";
  const nivelExemplo = 50;
  return (
    <form action={acao} className="mb-8 rounded-xl border border-ouro-escuro bg-pedra-900 p-5">
      <input type="hidden" name="id" value={a.id} />
      <input type="hidden" name="itemBase" value={a.itemBase} />
      <input type="hidden" name="preco" value={a.preco} />
      <input type="hidden" name="textura" value={a.textura} />
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-titulo text-2xl text-ouro">{a.id ? `Editar ${inicial.nome}` : "Nova arma"}</h2>
        <button type="button" onClick={fechar} className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">Fechar</button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label><span className={rotulo}>Nome</span><input name="nome" required value={a.nome} onChange={(e) => mudar("nome", e.target.value)} className={campo} placeholder="Lâmina do Crepúsculo" /></label>
            <label><span className={rotulo}>Código {a.id ? "(não muda)" : "(opcional)"}</span>
              <input name="codigo" value={a.codigo} readOnly={!!a.id} onChange={(e) => mudar("codigo", e.target.value)} className={`${campo} font-mono ${a.id ? "opacity-60" : ""}`} placeholder="criado a partir do nome" /></label>
          </div>
          <label className="block"><span className={rotulo}>História da arma (aparece na descrição)</span>
            <textarea name="descricao" rows={2} maxLength={240} value={a.descricao} onChange={(e) => mudar("descricao", e.target.value)} className={campo} placeholder="Forjada na última luz do dia..." /></label>
          <div><span className={rotulo}>Item base (dá o tipo e o jeito de bater: espada, machado, arco...)</span>
            <CampoItem valor={a.itemBase} onChange={(v) => mudar("itemBase", v)} titulo="Item base da arma" /></div>
          <div className="grid gap-3 sm:grid-cols-3">
            <label><span className={rotulo}>Rank para usar</span>
              <select name="rank" value={a.rank} onChange={(e) => mudar("rank", Number(e.target.value))} className={campo}>{RANKS.map((r, i) => <option key={r} value={i}>Rank {r}</option>)}</select></label>
            <label><span className={rotulo}>Raridade</span>
              <select name="raridade" value={a.raridade} onChange={(e) => mudar("raridade", Number(e.target.value))} className={campo}>{RARIDADES.map(([r], i) => <option key={r} value={i}>{r}</option>)}</select></label>
            <label><span className={rotulo}>Dano extra</span><input name="dano" type="number" step="0.5" min={0} value={a.dano} onChange={(e) => mudar("dano", Number(e.target.value))} className={campo} /></label>
          </div>
          <label className="block"><span className={rotulo}>Encantamentos (opcional)</span>
            <input name="encantos" value={a.encantos} onChange={(e) => mudar("encantos", e.target.value)} className={`${campo} font-mono`} placeholder="minecraft:sharpness*3, minecraft:unbreaking*3" /></label>

          <div className="rounded-lg border border-pedra-700 p-3">
            <p className="mb-2 font-titulo text-lg text-ouro">Quantas existem e como se consegue</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label><span className={rotulo}>Total no mundo (0 = sem limite)</span><input name="limite" type="number" min={0} value={a.limite} onChange={(e) => mudar("limite", Number(e.target.value))} className={campo} /></label>
              <label className="flex items-center gap-2 self-end pb-2 text-sm"><input type="checkbox" name="umaPorJogador" checked={a.umaPorJogador} onChange={(e) => mudar("umaPorJogador", e.target.checked)} /> Só uma por jogador</label>
            </div>
            <label className="mt-3 flex items-center gap-2 text-sm"><input type="checkbox" name="inquebravel" checked={a.inquebravel} onChange={(e) => mudar("inquebravel", e.target.checked)} /> Inquebrável (nunca desgasta)</label>
            <label className="mt-3 flex items-center gap-2 text-sm"><input type="checkbox" name="ferreiro" checked={a.ferreiro} onChange={(e) => mudar("ferreiro", e.target.checked)} /> À venda no ferreiro (ou no armeiro, se for armadura)</label>
            {a.ferreiro && (
              <div className="mt-2 grid gap-3 sm:grid-cols-2">
                <div><span className={rotulo}>Preço</span><CampoMoedas cobre={a.preco} onChange={(v) => mudar("preco", v)} /></div>
                <label><span className={rotulo}>Nível mínimo para aparecer</span><input name="nivelMin" type="number" min={1} value={a.nivelMin} onChange={(e) => mudar("nivelMin", Number(e.target.value))} className={campo} /></label>
              </div>
            )}
            {!a.ferreiro && <input type="hidden" name="nivelMin" value={a.nivelMin} />}
            <p className="mt-3 text-xs text-pedra-500">Como recompensa de missão: na missão, adicione um item, clique em “id” e digite <code className="rounded bg-pedra-800 px-1 text-ouro">codex:{a.codigo || "codigo_da_arma"}</code>.</p>
          </div>

          <div className="rounded-lg border border-pedra-700 p-3">
            <p className="mb-2 text-sm"><b>Como o item evolui</b> <span className="text-xs text-pedra-500">(escolha um)</span></p>
            {a.evolui && <input type="hidden" name="evolui" value="on" />}
            {a.nivelUso && <input type="hidden" name="nivelUso" value="on" />}
            <div className="grid gap-2">
              <label className="flex items-start gap-2 text-sm">
                <input type="radio" name="evolucao" className="mt-1" checked={!a.evolui && !a.nivelUso} onChange={() => { mudar("evolui", false); mudar("nivelUso", false); }} />
                <span>Não evolui<span className="block text-xs text-pedra-500">O poder é sempre o mesmo.</span></span>
              </label>
              <label className="flex items-start gap-2 text-sm">
                <input type="radio" name="evolucao" className="mt-1" checked={a.evolui} onChange={() => { mudar("evolui", true); mudar("nivelUso", false); }} />
                <span>Com o nível do jogador<span className="block text-xs text-pedra-500">Fica vinculada a quem recebeu (só ele empunha) e ganha dano a cada nível do dono.</span></span>
              </label>
              <label className="flex items-start gap-2 text-sm">
                <input type="radio" name="evolucao" className="mt-1" checked={a.nivelUso} onChange={() => { mudar("evolui", false); mudar("nivelUso", true); }} />
                <span>Com o uso (nível do próprio item, 10 níveis, bem difícil)<span className="block text-xs text-pedra-500">Arma: sobe com o dano em monstros, o golpe final vale o dobro, +2% de dano por nível. Armadura: sobe com o dano recebido de monstros, −1% de dano recebido por nível. Teto de 25.000 de experiência por dia; chefões valem 5×; spawner não conta. O nível fica no item, mesmo se trocar de dono.</span></span>
              </label>
            </div>
            {a.evolui ? (
              <div className="mt-2 grid gap-3 sm:grid-cols-2">
                <label><span className={rotulo}>Dano a mais por nível do dono</span><input name="danoPorNivel" type="number" step="0.01" min={0} value={a.danoPorNivel} onChange={(e) => mudar("danoPorNivel", Number(e.target.value))} className={campo} /></label>
                <p className="self-end pb-2 text-xs text-pergaminho/80">No nível {nivelExemplo} do dono: +{(a.dano + a.danoPorNivel * nivelExemplo).toFixed(1)} de dano. No nível 100: +{(a.dano + a.danoPorNivel * 100).toFixed(1)}.</p>
              </div>
            ) : <input type="hidden" name="danoPorNivel" value={a.danoPorNivel} />}
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="ativa" checked={a.ativa} onChange={(e) => mudar("ativa", e.target.checked)} /> Ativa (desmarque para tirar de circulação sem apagar)</label>
        </div>

        <div>
          <span className={rotulo}>Aparência da arma</span>
          <EditorPixel valor={a.textura} aoMudar={(png) => mudar("textura", png)} itemBase={a.itemBase} />
          <div className="mt-3 flex items-center gap-3 rounded-lg border border-pedra-700 bg-pedra-950 p-3">
            <span className="grid h-14 w-14 place-items-center rounded bg-pedra-800"><Aparencia a={a} tam={48} /></span>
            <div className="min-w-0 text-sm">
              <p className="truncate font-bold" style={{ color: RARIDADES[a.raridade]?.[1] }}>{a.nome || "Nome da arma"}</p>
              <p className="text-xs" style={{ color: RARIDADES[a.raridade]?.[1] }}>★ {RARIDADES[a.raridade]?.[0]}</p>
              {a.descricao && <p className="text-xs text-pedra-500">{a.descricao}</p>}
              {a.dano > 0 && <p className="text-xs text-red-300">+{a.dano.toFixed(1)} de dano</p>}
              {a.evolui && <p className="text-xs text-purple-300">Evolui com você: +{a.danoPorNivel} por nível</p>}
              <p className="text-xs text-ouro">Arma única{a.limite > 0 ? ` · só ${a.limite} no mundo` : ""} · Classe {RANKS[a.rank]}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button disabled={enviando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:brightness-110 disabled:opacity-50">{enviando ? "Salvando..." : "Salvar arma"}</button>
        {estado?.erro && <span className="text-sm text-red-300">{estado.erro}</span>}
        {estado?.ok && <span className="text-sm text-green-300">{estado.ok}</span>}
      </div>
    </form>
  );
}

export function TelaCodex({ armas, armasNoPacote }: { armas: Arma[]; armasNoPacote: number }) {
  const [editando, setEditando] = useState<Arma | null>(null);
  return (
    <>
      {editando ? <Editor key={editando.id || "nova"} inicial={editando} fechar={() => setEditando(null)} /> : (
        <button type="button" onClick={() => setEditando(NOVA)} className="mb-6 rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:brightness-110">+ Nova arma</button>
      )}
      {!armas.length && !editando && <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-6 text-pedra-500">O Códex está vazio. Crie a primeira arma única do servidor.</p>}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {armas.map((a) => {
          const esgotada = a.limite > 0 && a.emitidas >= a.limite;
          return (
            <div key={a.id} className={`rounded-xl border bg-pedra-900 p-4 ${a.ativa ? "border-pedra-700" : "border-pedra-700 opacity-60"}`}>
              <div className="flex items-start gap-3">
                <span className="grid h-14 w-14 shrink-0 place-items-center rounded bg-pedra-800"><Aparencia a={a} tam={48} /></span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-titulo text-lg" style={{ color: RARIDADES[a.raridade]?.[1] }}>{a.nome}</p>
                  <p className="text-xs text-pedra-500">Classe {RANKS[a.rank]} · {RARIDADES[a.raridade]?.[0]}{a.evolui ? " · evolui com o jogador" : a.nivelUso ? " · evolui com o uso" : ""}{a.ativa ? "" : " · inativa"}</p>
                  <p className="font-mono text-[11px] text-pedra-500">codex:{a.codigo}</p>
                </div>
              </div>
              {a.descricao && <p className="mt-2 text-xs text-pergaminho/70">{a.descricao}</p>}
              <div className="mt-3">
                <div className="flex justify-between text-xs"><span className="text-pedra-500">No mundo</span><span className={esgotada ? "text-red-300" : "text-ouro"}>{a.emitidas}{a.limite > 0 ? ` de ${a.limite}` : " (sem limite)"}{esgotada ? " · esgotada" : ""}</span></div>
                {a.limite > 0 && <div className="mt-1 h-2 overflow-hidden rounded bg-pedra-800"><div className={`h-full ${esgotada ? "bg-sangue" : "bg-ouro-escuro"}`} style={{ width: `${Math.min(100, (a.emitidas / a.limite) * 100)}%` }} /></div>}
                {!!a.donos.length && <p className="mt-1 truncate text-[11px] text-pedra-500" title={a.donos.join(", ")}>Com: {a.donos.join(", ")}</p>}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-pedra-500">
                {a.ferreiro ? <span className="flex items-center gap-1">Ferreiro: <Moedas cobre={a.preco} tam={12} /> · nível {a.nivelMin}+</span> : <span>Só por missão</span>}
                <span>+{a.dano} de dano{a.evolui ? ` +${a.danoPorNivel}/nível` : ""}</span>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" onClick={() => { setEditando(a); window.scrollTo({ top: 0, behavior: "smooth" }); }} className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">Editar</button>
                {a.emitidas > 0 && (
                  <form action={zerarContagem} onSubmit={(e) => { if (!window.confirm(`Zerar a contagem de ${a.nome}? As armas já entregues continuam com os jogadores, mas o total volta a ficar disponível.`)) e.preventDefault(); }}>
                    <input type="hidden" name="codigo" value={a.codigo} /><button className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">Zerar contagem</button>
                  </form>
                )}
                <form action={excluirArma} onSubmit={(e) => { if (!window.confirm(`Apagar ${a.nome} do Códex? As armas já entregues viram itens comuns no próximo carregamento.`)) e.preventDefault(); }}>
                  <input type="hidden" name="id" value={a.id} /><button className="rounded-md border border-sangue px-3 py-1 text-xs text-red-300 hover:bg-sangue/20">Apagar</button>
                </form>
              </div>
            </div>
          );
        })}
      </div>
      <p className="mt-6 text-xs text-pedra-500">
        {armasNoPacote > 0 ? `${armasNoPacote} arma(s) com aparência própria no pacote de recursos. Os jogadores recebem o pacote ao entrar e precisam aceitar para ver os desenhos.` : "Nenhuma arma com aparência própria ainda: sem pacote de recursos para os jogadores."}
        {" "}O servidor relê o Códex a cada minuto. Comandos de admin: /frx codex lista · /frx codex dar &lt;jogador&gt; &lt;código&gt;.
      </p>
    </>
  );
}
