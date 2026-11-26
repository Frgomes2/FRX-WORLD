/**
 * Diagnóstico das quedas do painel (roda uma vez, quando o servidor do Next sobe).
 * Grava em logs/painel-saida.log:
 *  - a cada minuto, a memória do processo (para ver se ela cresce até estourar);
 *  - quem chamou process.exit (com a pilha), o código de saída e exceções não tratadas.
 * Se o painel morrer e este arquivo não registrar nada, o processo foi encerrado de fora (por outro programa).
 */
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  const fs = await import("fs");
  const path = await import("path");
  const arq = path.resolve(process.cwd(), "..", "logs", "painel-saida.log");
  const g = globalThis as unknown as { frxDiag?: boolean };
  if (g.frxDiag) return;
  g.frxDiag = true;

  const anotar = (texto: string) => {
    try { fs.appendFileSync(arq, `${new Date().toLocaleString("pt-BR")} [pid ${process.pid}] ${texto}\n`); } catch { /* sem pasta */ }
  };
  const mb = (n: number) => Math.round(n / 1048576);
  anotar("painel subiu");

  setInterval(() => {
    const m = process.memoryUsage();
    anotar(`memória: rss ${mb(m.rss)} MB, heap ${mb(m.heapUsed)}/${mb(m.heapTotal)} MB, externa ${mb(m.external)} MB`);
  }, 60_000).unref();

  const sairOriginal = process.exit.bind(process);
  process.exit = ((codigo?: number) => {
    anotar(`process.exit(${codigo ?? ""}) chamado por:\n${new Error().stack}`);
    return sairOriginal(codigo);
  }) as typeof process.exit;

  process.on("exit", (codigo) => anotar(`saindo (código ${codigo})`));
  process.on("uncaughtException", (e) => anotar(`exceção não tratada: ${e?.stack ?? e}`));
  process.on("unhandledRejection", (e) => anotar(`promessa rejeitada sem tratamento: ${(e as Error)?.stack ?? e}`));
  process.on("warning", (w) => anotar(`aviso do node: ${w.name}: ${w.message}`));
}
