import { AbasServidor } from "./abas";

export default function ServidorPage() {
  return (
    <>
      <h1 className="font-titulo text-3xl">Servidor</h1>
      <p className="mb-6 text-pedra-500">
        Liga, desliga e reinicia o servidor do FRX-WORLD (parar sempre salva o mundo). Em Monitoramento ficam os erros, travadas, crashes e a saúde do servidor.
      </p>
      <AbasServidor />
    </>
  );
}
