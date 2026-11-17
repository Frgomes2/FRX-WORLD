import { prisma } from "@/lib/prisma";
import { criarConvite, revogarConvite } from "./actions";
import { Abertura } from "./abertura";
import { lerConfig } from "@/lib/config-jogo";

const data = (d: Date) => d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" });

export default async function ConvitesPage() {
  const config = await lerConfig();
  const convites = await prisma.convite.findMany({
    orderBy: { criadoEm: "desc" },
    include: { jogador: true },
  });

  return (
    <>
      <h1 className="font-titulo text-3xl">Convites</h1>
      <p className="mb-6 text-pedra-500">
        Cada código vale para um único jogador. No primeiro login ele digita <code className="text-ouro">/convite CÓDIGO</code>.
      </p>

      <form action={criarConvite} className="mb-8 flex gap-3">
        <input name="nota" placeholder="Para quem é? (opcional)"
          className="w-72 rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 outline-none focus:border-ouro" />
        <button className="rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:bg-ouro-escuro">
          Gerar convite
        </button>
      </form>

      <div className="overflow-x-auto rounded-xl border border-pedra-700">
        <table className="w-full text-sm">
          <thead className="bg-pedra-800 text-left text-pedra-500">
            <tr>
              <th className="px-4 py-3">Código</th>
              <th className="px-4 py-3">Nota</th>
              <th className="px-4 py-3">Situação</th>
              <th className="px-4 py-3">Criado em</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {convites.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-pedra-500">Nenhum convite ainda.</td></tr>
            )}
            {convites.map((c) => {
              let situacao: React.ReactNode;
              switch (true) {
                case c.revogado:
                  situacao = <span className="text-red-400">Revogado</span>;
                  break;
                case !!c.usadoEm:
                  situacao = <span className="text-green-400">Usado por {c.jogador?.nick ?? "?"}</span>;
                  break;
                default:
                  situacao = <span className="text-ouro">Disponível</span>;
              }
              return (
                <tr key={c.id} className="border-t border-pedra-700">
                  <td className="px-4 py-3 font-mono text-base">{c.codigo}</td>
                  <td className="px-4 py-3 text-pedra-500">{c.nota ?? "—"}</td>
                  <td className="px-4 py-3">{situacao}</td>
                  <td className="px-4 py-3 text-pedra-500">{data(c.criadoEm)}</td>
                  <td className="px-4 py-3 text-right">
                    {!c.usadoEm && !c.revogado && (
                      <form action={revogarConvite}>
                        <input type="hidden" name="id" value={c.id} />
                        <button className="text-red-400 hover:underline">Revogar</button>
                      </form>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <Abertura inicial={config.abertura} />
    </>
  );
}
