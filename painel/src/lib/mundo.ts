import "server-only";
import fs from "fs";
import path from "path";
import { servidorMc } from "@/lib/servidor-mc";

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}

/**
 * Gera um mundo novo: guarda o atual em server/mundos-antigos/ (nada é apagado)
 * e ajusta a seed no server.properties. O mundo novo nasce na próxima vez que o servidor ligar.
 */
export function novoMundo(seed?: string) {
  if (servidorMc.estado !== "parado") throw new Error("Pare o servidor antes de gerar um mundo novo.");

  const pasta = pastaServidor();
  const arqProps = path.join(pasta, "server.properties");
  let props = fs.existsSync(arqProps) ? fs.readFileSync(arqProps, "utf8") : "";
  const nome = props.match(/^level-name=(.*)$/m)?.[1]?.trim() || "world";

  let guardadoEm: string | null = null;
  const atual = path.join(pasta, nome);
  if (fs.existsSync(atual)) {
    const destinoDir = path.join(pasta, "mundos-antigos");
    fs.mkdirSync(destinoDir, { recursive: true });
    const carimbo = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "");
    guardadoEm = path.join(destinoDir, `${nome}-${carimbo}`);
    fs.renameSync(atual, guardadoEm);
  }

  const linhaSeed = `level-seed=${(seed ?? "").trim()}`;
  props = /^level-seed=.*$/m.test(props) ? props.replace(/^level-seed=.*$/m, linhaSeed) : `${props.trimEnd()}\n${linhaSeed}\n`;
  fs.writeFileSync(arqProps, props);

  return { guardadoEm: guardadoEm ? path.relative(pasta, guardadoEm) : null };
}
