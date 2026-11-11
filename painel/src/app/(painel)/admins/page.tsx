import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { removerAdmin } from "./actions";
import { MinhaSenha, NovoAdmin } from "./formularios";

const data = (d: Date) => d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" });

export default async function AdminsPage() {
  const sessao = await auth();
  const admins = await prisma.admin.findMany({ orderBy: { criadoEm: "asc" } });

  return (
    <>
      <h1 className="font-titulo text-3xl">Administradores</h1>
      <p className="mb-6 text-pedra-500">
        Quem está aqui entra no painel com e-mail e senha e pode mexer em tudo: servidor, jogadores, economia e conteúdo.
        Dê acesso só a quem você confia.
      </p>

      <div className="mb-8 overflow-x-auto rounded-xl border border-pedra-700">
        <table className="w-full text-sm">
          <thead className="bg-pedra-800 text-left text-pedra-500">
            <tr>
              <th className="px-4 py-3">Nome</th>
              <th className="px-4 py-3">E-mail</th>
              <th className="px-4 py-3">Desde</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {admins.map((a) => {
              const souEu = a.email === sessao?.user?.email;
              return (
                <tr key={a.id} className="border-t border-pedra-700">
                  <td className="px-4 py-3">{a.nome}{souEu && <span className="ml-2 text-xs text-ouro">(você)</span>}</td>
                  <td className="px-4 py-3 text-pedra-500">{a.email}</td>
                  <td className="px-4 py-3 text-pedra-500">{data(a.criadoEm)}</td>
                  <td className="px-4 py-3 text-right">
                    {!souEu && admins.length > 1 && (
                      <form action={removerAdmin}>
                        <input type="hidden" name="id" value={a.id} />
                        <button className="text-red-400 hover:underline">Remover</button>
                      </form>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="grid max-w-4xl gap-6 md:grid-cols-2">
        <NovoAdmin />
        <MinhaSenha />
      </div>
    </>
  );
}
