import { prisma } from "@/lib/prisma";
import { CLASSES, TIPOS } from "@/lib/habilidades";
import { carregarSugestoesHabilidades, excluirHabilidade } from "./actions";
import { FormHabilidade } from "./form";
import { IconeItem } from "@/components/itens";

export default async function HabilidadesPage() {
  const todas = await prisma.habilidade.findMany({ orderBy: [{ ultimate: "asc" }, { nivelReq: "asc" }, { nome: "asc" }] });
  const porClasse = await prisma.jogador.groupBy({ by: ["classe"], _count: true });
  const qtd = (c: string) => porClasse.find((p) => p.classe === c)?._count ?? 0;

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Habilidades</h1>
          <p className="text-pedra-500">
            Cada jogador escolhe uma classe (tecla K) e usa até 4 habilidades + 1 ultimate nas teclas R, G, V, C e X.
            As de &quot;Nível&quot; liberam sozinhas; as de &quot;Tomo Arcano&quot; vêm do item tomo (coloque <code>frxworld:tomo_arcano</code> no loot).
            Cada habilidade sobe de nível com o uso (até 10): mais dano e recarga menor.
          </p>
        </div>
        <form action={carregarSugestoesHabilidades}>
          <button className="shrink-0 rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">
            {todas.length === 0 ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <details className="mb-6 rounded-xl border border-pedra-700 bg-pedra-900 p-4 text-sm">
        <summary className="cursor-pointer text-ouro">Como cada tipo usa os campos</summary>
        <ul className="mt-2 list-disc pl-5 text-pedra-500">
          {Object.entries(TIPOS).map(([k, v]) => <li key={k}><b className="text-ouro">{k}</b>: {v}</li>)}
        </ul>
      </details>

      <nav className="mb-4 flex flex-wrap gap-2 text-sm">
        {Object.entries(CLASSES).map(([classe, info]) => (
          <a key={classe} href={`#${classe}`} className="rounded-full border border-pedra-700 px-3 py-1 hover:border-ouro hover:text-ouro">
            {info.nome} <span className="text-pedra-500">{todas.filter((h) => h.classe === classe).length}</span>
          </a>
        ))}
      </nav>

      {Object.entries(CLASSES).map(([classe, info]) => {
        const lista = todas.filter((h) => h.classe === classe);
        return (
          <section key={classe} id={classe} className="mb-6 scroll-mt-20 rounded-xl border border-pedra-700 bg-pedra-900 p-3 md:p-5">
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-4">
              <h2 className="font-titulo text-xl text-ouro">{info.nome} <span className="text-sm text-pedra-500">· {info.recurso}</span></h2>
              <span className="text-xs text-pedra-500">{info.estilo} · {qtd(classe)} jogador(es)</span>
            </div>
            <div className="flex flex-col gap-2">
              {lista.map((h) => (
                <details key={h.id} className={`group rounded-lg border border-pedra-800 open:border-ouro/40 open:bg-pedra-950/40 ${h.ativa ? "" : "opacity-50"}`}>
                  <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 p-2.5 hover:bg-pedra-800/50">
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-pedra-950"><IconeItem id={h.icone} tam={24} /></span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <b>{h.nome}</b>
                        <span className={`rounded-full border px-2 py-0.5 text-[11px] ${h.ultimate ? "border-red-500/60 text-red-300" : "border-pedra-700 text-pedra-500"}`}>{h.ultimate ? "ULTIMATE" : h.tipo}</span>
                        {h.aprendizado === "ITEM" && <span className="rounded-full border border-purple-500/50 px-2 py-0.5 text-[11px] text-purple-300">📖 Tomo</span>}
                        {!h.ativa && <span className="text-[11px] text-pedra-500">(desativada)</span>}
                      </span>
                      <span className="block truncate text-xs text-pedra-500">{h.descricao}</span>
                    </span>
                    <span className="flex shrink-0 items-center gap-3 font-mono text-xs text-pedra-500">
                      <span title="Nível necessário" className="text-ouro">Nv {h.nivelReq}</span>
                      <span title="Custo">💧{h.custo}</span>
                      <span title="Recarga">⏳{h.cooldown}s</span>
                      {h.dano !== 0 && <span title={h.dano < 0 ? "Cura" : "Dano"}>{h.dano < 0 ? "💚" : "⚔️"}{Math.abs(h.dano)}</span>}
                    </span>
                    <span className="text-xs text-pedra-500 group-open:hidden">editar ▾</span>
                    <span className="hidden text-xs text-ouro group-open:inline">fechar ▴</span>
                  </summary>
                  <div className="border-t border-pedra-800 p-3">
                    <FormHabilidade h={h} />
                    <form action={excluirHabilidade} className="mt-2 text-right"><input type="hidden" name="id" value={h.id} /><button className="text-xs text-red-400 hover:underline">Excluir habilidade</button></form>
                  </div>
                </details>
              ))}
              {lista.length === 0 && <p className="text-sm text-pedra-500">Nenhuma habilidade ainda.</p>}
            </div>
          </section>
        );
      })}

      <details className="rounded-xl border border-dashed border-pedra-700 p-4">
        <summary className="cursor-pointer font-titulo text-xl hover:text-ouro">+ Nova habilidade</summary>
        <div className="mt-3">
        <FormHabilidade h={{ codigo: "", nome: "", descricao: "", classe: "MAGO", tipo: "SKILLSHOT", elemento: "FOGO", icone: "minecraft:blaze_powder", custo: 20, cooldown: 5, dano: 8, alcance: 20, raio: 0, duracao: 0, efeito: "", potencia: 0, nivelReq: 1, ultimate: false, aprendizado: "NIVEL", ativa: true }} />
        </div>
      </details>
    </>
  );
}
