import Link from "next/link";
import { prisma } from "@/lib/prisma";
import { alternarAcesso } from "./actions";
import { CLASSES } from "@/lib/habilidades";

const data = (d: Date | null) => (d ? d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" }) : "—");

export default async function JogadoresPage() {
  const jogadores = await prisma.jogador.findMany({ orderBy: [{ nivel: "desc" }, { criadoEm: "asc" }] });

  return (
    <>
      <h1 className="font-titulo text-3xl">Jogadores</h1>
      <p className="mb-6 text-pedra-500">Contas que já entraram com convite. Clique no nick para ver a ficha completa (inventário, habilidades, conquistas e ferramentas de admin). Revogar bloqueia a entrada no servidor.</p>

      <div className="overflow-x-auto rounded-xl border border-pedra-700">
        <table className="w-full text-sm">
          <thead className="bg-pedra-800 text-left text-pedra-500">
            <tr>
              <th className="px-4 py-3">Nick</th>
              <th className="px-4 py-3">Nível</th>
              <th className="px-4 py-3">Rank</th>
              <th className="px-4 py-3">Classe</th>
              <th className="px-4 py-3">Conquistas</th>
              <th className="px-4 py-3">Mortes</th>
              <th className="px-4 py-3">Acesso</th>
              <th className="px-4 py-3">Último login</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {jogadores.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-6 text-center text-pedra-500">Ninguém entrou ainda.</td></tr>
            )}
            {jogadores.map((j) => (
              <tr key={j.id} className="border-t border-pedra-700">
                <td className="px-4 py-3 font-semibold">
                  <Link href={`/admin/jogadores/${j.uuid}`} className="group flex items-center gap-2.5">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={`https://mc-heads.net/avatar/${j.uuid}/28`} alt="" width={28} height={28} className="rounded [image-rendering:pixelated]" />
                    <span>
                      <span className="group-hover:text-ouro group-hover:underline">{j.nick}</span>
                      {j.online && <span className="ml-1.5 text-xs text-green-400">● online</span>}
                      {j.titulo && <span className="block text-xs font-normal text-purple-300">«{j.titulo}»</span>}
                    </span>
                  </Link>
                </td>
                <td className="px-4 py-3">{j.nivel}</td>
                <td className="px-4 py-3 font-titulo text-lg text-ouro">{j.rank}</td>
                <td className="px-4 py-3">{CLASSES[j.classe as keyof typeof CLASSES]?.nome ?? <span className="text-pedra-500">—</span>}</td>
                <td className="px-4 py-3">🏆 {j.conquistas}</td>
                <td className="px-4 py-3 text-pedra-500">{j.mortes}</td>
                <td className="px-4 py-3">{j.ativo ? <span className="text-green-400">Liberado</span> : <span className="text-red-400">Revogado</span>}</td>
                <td className="px-4 py-3 text-pedra-500">{data(j.ultimoLogin)}</td>
                <td className="px-4 py-3 text-right">
                  <form action={alternarAcesso}>
                    <input type="hidden" name="id" value={j.id} />
                    <button className={j.ativo ? "text-red-400 hover:underline" : "text-ouro hover:underline"}>
                      {j.ativo ? "Revogar" : "Liberar"}
                    </button>
                  </form>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
