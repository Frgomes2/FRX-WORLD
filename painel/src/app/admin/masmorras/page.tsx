import { prisma } from "@/lib/prisma";
import { alternarMasmorra, excluirMasmorra, refazerMasmorra } from "./actions";
import { FormMasmorra } from "./linha";

const data = (d: Date | null) => (d ? d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" }) : "");
const botao = "rounded-md border border-pedra-700 px-2.5 py-1 text-xs hover:border-ouro";
const bonito = (id: string) => (id.split(":")[1] ?? id).replace(/[_/]/g, " ");

export default async function MasmorrasPage() {
  const lista = await prisma.masmorra.findMany({ orderBy: [{ ordem: "asc" }, { id: "asc" }] });
  const noMundo = lista.filter((m) => m.colocadaX !== null).length;
  const esperando = lista.filter((m) => m.ativo && (m.colocadaX === null || m.refazer)).length;

  return (
    <>
      <h1 className="font-titulo text-3xl">Masmorras</h1>
      <p className="mb-5 max-w-3xl text-pedra-500">
        Estruturas grandes de outros mods em pontos fixos do mapa. O servidor coloca cada uma <b>uma vez por mundo, ao ligar</b>,
        já com <b>5 baús de tesouro</b>; quem derrota o chefe lá dentro ganha mais um <b>baú do chefe</b>. No jogo: <code>/masmorras</code>.
      </p>

      <div className="mb-5 grid grid-cols-3 gap-3 sm:max-w-xl">
        {[["Cadastradas", lista.length], ["No mundo", noMundo], ["Surgem ao ligar", esperando]].map(([t, v]) => (
          <div key={t} className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">{t}</p>
            <p className="font-titulo text-2xl text-ouro">{v}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {lista.map((m) => {
          const colocada = m.colocadaX !== null;
          const opcoes = m.estrutura.split("|");
          return (
            <section key={m.id} className={`rounded-xl border bg-pedra-900 p-4 ${colocada ? "border-pedra-700" : "border-yellow-500/30"} ${m.ativo ? "" : "opacity-50"}`}>
              <div className="flex flex-wrap items-start gap-3">
                <span className="grid h-12 w-12 shrink-0 place-items-center rounded-lg bg-pedra-950 text-2xl">☠</span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-titulo text-xl text-ouro">{m.nome}</h2>
                    {!m.ativo ? <span className="rounded-full bg-pedra-700 px-2 py-0.5 text-xs">desativada</span>
                      : m.refazer ? <span className="rounded-full bg-yellow-500/20 px-2 py-0.5 text-xs text-yellow-200">será recolocada ao ligar</span>
                      : colocada ? <span className="rounded-full bg-green-500/20 px-2 py-0.5 text-xs text-green-300">● no mundo</span>
                      : <span className="rounded-full bg-yellow-500/20 px-2 py-0.5 text-xs text-yellow-200">surge ao ligar o servidor</span>}
                  </div>
                  <p className="font-mono text-xs text-pedra-500">{m.id}</p>
                </div>
              </div>

              <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <div><dt className="text-xs text-pedra-500">Chefe</dt><dd>{m.chefe || "—"}</dd></div>
                <div><dt className="text-xs text-pedra-500">Ponto pedido</dt><dd className="font-mono">{m.x}, {m.z}</dd></div>
                <div className="col-span-2"><dt className="text-xs text-pedra-500">Estrutura {opcoes.length > 1 ? "(vale a primeira que existir)" : ""}</dt>
                  <dd className="flex flex-wrap gap-1.5">
                    {opcoes.map((e) => (
                      <span key={e} title={e} className={`rounded-md border px-1.5 py-0.5 text-xs capitalize ${m.colocadaEstrutura === e ? "border-green-500/50 text-green-300" : "border-pedra-700"}`}>{bonito(e)}</span>
                    ))}
                  </dd></div>
                {colocada && (
                  <div className="col-span-2 rounded-lg bg-pedra-950/60 p-2">
                    <dt className="text-xs text-pedra-500">Onde ficou · {data(m.colocadaEm)}</dt>
                    <dd className="font-mono text-green-300">/tp @s {m.colocadaX} {m.colocadaY} {m.colocadaZ}</dd>
                  </div>
                )}
                {m.entradaX !== null && <div className="col-span-2"><dt className="text-xs text-pedra-500">Entrada (poço com escada)</dt><dd className="font-mono">{m.entradaX}, {m.entradaY}, {m.entradaZ}</dd></div>}
              </dl>

              <details className="mt-3 border-t border-pedra-800 pt-3">
                <summary className="cursor-pointer text-sm text-pedra-500 hover:text-ouro">Editar e ações</summary>
                <div className="mt-3 space-y-3">
                  <FormMasmorra valores={m} />
                  <div className="flex flex-wrap items-center gap-2">
                    <form action={refazerMasmorra}><input type="hidden" name="id" value={m.id} />
                      <button className={botao} title="Gera de novo no próximo início (a antiga continua no mundo)">{m.refazer ? "Cancelar recolocar" : "Recolocar"}</button></form>
                    <form action={alternarMasmorra}><input type="hidden" name="id" value={m.id} />
                      <button className={botao}>{m.ativo ? "Desativar" : "Ativar"}</button></form>
                    <form action={excluirMasmorra}><input type="hidden" name="id" value={m.id} />
                      <button className={`${botao} text-red-400 hover:border-red-400`}>Excluir</button></form>
                  </div>
                  <p className="text-xs text-pedra-500">Mudar a posição de uma masmorra que já surgiu não move nada: use <b>Recolocar</b>.</p>
                </div>
              </details>
            </section>
          );
        })}
      </div>
      {lista.length === 0 && <p className="mb-4 text-sm text-pedra-500">Nenhuma masmorra cadastrada ainda.</p>}

      <details className="mt-4 rounded-xl border border-dashed border-pedra-700 p-4">
        <summary className="cursor-pointer font-titulo text-xl hover:text-ouro">+ Nova masmorra</summary>
        <div className="mt-3">
          <FormMasmorra />
          <p className="mt-3 text-xs text-pedra-500">
            Exemplos de estruturas: cataclysm:ancient_factory, cataclysm:frosted_prison, cataclysm:cursed_pyramid, cataclysm:burning_arena,
            cataclysm:soul_black_smith, mowziesmobs:wrought_chamber, mowziesmobs:monastery, iceandfire:gorgon_temple,
            dungeons_arise:foundry, dungeons_arise:keep_kayra, dungeons_arise:shiraz_palace, dungeons_arise:plague_asylum.
          </p>
        </div>
      </details>
    </>
  );
}
