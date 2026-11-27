export const dynamic = "force-dynamic";
export const metadata = { title: "Painel", robots: { index: false, follow: false } };

import { redirect } from "next/navigation";
import { auth, signOut } from "@/auth";
import { Menu, MenuCelular } from "@/components/menu";

export default async function PainelLayout({ children }: { children: React.ReactNode }) {
  const sessao = await auth();
  if (!sessao?.user) redirect("/login");

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-64 shrink-0 overflow-y-auto border-r border-pedra-700 bg-pedra-900 p-4 md:sticky md:top-0 md:block md:h-screen">
        <div className="mb-6 px-3">
          <p className="font-titulo text-2xl text-ouro">FRX-WORLD</p>
          <p className="text-xs text-pedra-500">Painel admin</p>
        </div>
        <Menu />
      </aside>
      <div className="min-w-0 flex-1">
        <header className="flex items-center justify-between gap-3 border-b border-pedra-700 px-4 py-3 text-sm md:justify-end md:px-8">
          <MenuCelular />
          <div className="flex shrink-0 items-center gap-3">
          <span className="hidden text-pedra-500 sm:inline">{sessao.user.name}</span>
          <form action={async () => { "use server"; await signOut({ redirectTo: "/login" }); }}>
            <button className="rounded-md border border-pedra-700 px-3 py-1 hover:border-ouro">Sair</button>
          </form>
          </div>
        </header>
        <main className="p-4 md:p-8">{children}</main>
      </div>
    </div>
  );
}
