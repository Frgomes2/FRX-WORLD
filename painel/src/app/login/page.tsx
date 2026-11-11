import { FormLogin } from "./form";

export default function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-sm rounded-xl border border-pedra-700 bg-pedra-900/80 p-8 shadow-2xl">
        <h1 className="font-titulo text-3xl text-ouro">FRX-WORLD</h1>
        <p className="mb-6 text-sm text-pedra-500">Painel do administrador</p>
        <FormLogin />
      </div>
    </main>
  );
}
