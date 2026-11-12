import { prisma } from "@/lib/prisma";
import { lootParaTexto } from "@/lib/dungeons";
import { lerConfigEventos } from "@/lib/eventos";
import { carregarSugestoesEventos, excluirEvento } from "./actions";
import { FormConfig, FormEvento } from "./linhas";

const TIPOS: Record<string, [string, string]> = { EMBOSCADA: ["⚔️", "Emboscada"], CHEFE: ["👹", "Chefe errante"], MERCADOR: ["🧙", "Mercador raro"], TESOURO: ["💎", "Tesouro"] };

export default async function EventosPage() {
  const [config, eventos] = await Promise.all([
    lerConfigEventos(),
    prisma.eventoEstrada.findMany({ orderBy: [{ nivelMin: "asc" }, { nome: "asc" }] }),
  ]);
  const pesoTotal = eventos.filter((e) => e.ativo).reduce((s, e) => s + e.peso, 0);

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Eventos de estrada</h1>
          <p className="text-pedra-500">
            Enquanto o jogador viaja, o servidor sorteia eventos pelo nível dele: emboscadas, chefes errantes, mercadores raros e tesouros.
            Quem derrotar todos os inimigos (ou chegar ao tesouro) ganha o XP e o loot.
          </p>
        </div>
        <form action={carregarSugestoesEventos}>
          <button className="shrink-0 rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">
            {eventos.length === 0 ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <section className="mb-8 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Frequência</h2>
        <FormConfig c={config} />
        <p className="mt-3 text-xs text-pedra-500">
          Com estes valores, quem está viajando tem em média 1 evento a cada{" "}
          {config.chance > 0 ? Math.round(Math.max(config.checagemSegundos / (config.chance / 100), config.recargaMinutos * 60 + config.checagemSegundos) / 60) : "∞"} min.
        </p>
      </section>

      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-3 md:p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Eventos <span className="text-sm text-pedra-500">· {eventos.length}</span></h2>
        <div className="flex flex-col gap-2">
          {eventos.map((e) => {
            const [icone, tipo] = TIPOS[e.tipo] ?? ["❔", e.tipo];
            return (
              <details key={e.id} className={`group rounded-lg border border-pedra-800 open:border-ouro/40 open:bg-pedra-950/40 ${e.ativo ? "" : "opacity-50"}`}>
                <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 p-2.5 hover:bg-pedra-800/50">
                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-pedra-950 text-lg">{icone}</span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <b>{e.nome}</b>
                      <span className="rounded-full border border-pedra-700 px-2 py-0.5 text-[11px] text-pedra-500">{tipo}</span>
                      <span className="rounded-full border border-ouro/40 px-2 py-0.5 text-[11px] text-ouro">Nv {e.nivelMin}{e.nivelMax >= 1000 ? "+" : `–${e.nivelMax}`}</span>
                    </span>
                    <span className="block truncate text-xs text-pedra-500">{e.mensagem}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-3 font-mono text-xs text-pedra-500">
                    {e.tipo !== "TESOURO" && <span title="Criatura">{e.mobQtd > 1 ? `${e.mobQtd}× ` : ""}{e.mobTipo.split(":")[1]?.replace(/_/g, " ")}</span>}
                    {e.recompensaXp > 0 && <span title="XP">✨{e.recompensaXp}</span>}
                    <span title="Chance entre os eventos do nível" className="text-pergaminho">{e.ativo && pesoTotal > 0 ? `${Math.round((e.peso / pesoTotal) * 100)}%` : "off"}</span>
                  </span>
                  <span className="text-xs text-pedra-500 group-open:hidden">editar ▾</span>
                  <span className="hidden text-xs text-ouro group-open:inline">fechar ▴</span>
                </summary>
                <div className="border-t border-pedra-800 p-3">
                  <FormEvento e={{ ...e, loot: lootParaTexto(e.loot) }} />
                  <form action={excluirEvento} className="mt-2 text-right"><input type="hidden" name="id" value={e.id} /><button className="text-xs text-red-400 hover:underline">Excluir evento</button></form>
                </div>
              </details>
            );
          })}
          <details className="rounded-lg border border-dashed border-pedra-700 p-3">
            <summary className="cursor-pointer text-sm hover:text-ouro">+ Novo evento</summary>
            <div className="mt-3">
              <FormEvento e={{ nome: "", tipo: "EMBOSCADA", mensagem: "", peso: 10, nivelMin: 1, nivelMax: 1000, mobTipo: "minecraft:zombie", mobQtd: 4, vida: 0, dano: 0, preco: 0, recompensaXp: 200, loot: "", ativo: true }} />
            </div>
          </details>
        </div>
      </section>
    </>
  );
}
