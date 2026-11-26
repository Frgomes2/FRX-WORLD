import "server-only";
import { spawn, type ChildProcessWithoutNullStreams } from "child_process";
import { EventEmitter } from "events";
import fs from "fs";
import net from "net";
import path from "path";

/**
 * Controla o processo do servidor Minecraft a partir do painel.
 * O painel roda na mesma máquina do servidor: ele mesmo inicia o Java, lê o console e manda comandos.
 *
 * O Java nasce separado do painel ("detached"): se o painel cair, o jogo continua no ar. O painel novo
 * "adota" o servidor que encontrar rodando (porta 25565 ocupada): lê o console pelo logs/latest.log e manda
 * comandos pelo arquivo frx_comandos.txt, que o core mod lê a cada meio segundo (acesso/ComandosArquivo).
 */

export type EstadoServidor = "parado" | "iniciando" | "online" | "parando";

export type EventoServidor =
  | { tipo: "linha"; texto: string }
  | { tipo: "estado"; estado: EstadoServidor; jogadores: string[]; iniciadoEm: number | null };

const MAX_LINHAS = 3000;
const ANSI = /\u001b\[[0-9;]*m/g;

function pastaServidor() {
  return path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
}

/** Java 17: JAVA17_HOME do .env ou as mesmas pastas que o java17.bat procura. */
function acharJava17(): string {
  const exe = process.platform === "win32" ? "java.exe" : "java";
  if (process.env.JAVA17_HOME) return path.join(process.env.JAVA17_HOME, "bin", exe);

  const raizes = [
    "C:\\Program Files\\Eclipse Adoptium",
    "C:\\Program Files\\Java",
    "C:\\Program Files\\Microsoft",
    "C:\\Program Files\\Zulu",
    path.resolve(process.cwd(), "../tools"),
  ];
  for (const raiz of raizes) {
    if (!fs.existsSync(raiz)) continue;
    const jdk = fs.readdirSync(raiz).find((n) => /^(jdk-|zulu-)?17/.test(n) || n.startsWith("jdk-17"));
    if (jdk) {
      const java = path.join(raiz, jdk, "bin", exe);
      if (fs.existsSync(java)) return java;
    }
  }
  return exe; // último recurso: o do PATH
}

/** Argumentos que o run.bat do Forge usaria. */
function argsForge(pasta: string): string[] {
  const forgeDir = path.join(pasta, "libraries", "net", "minecraftforge", "forge");
  const versao = fs.existsSync(forgeDir) ? fs.readdirSync(forgeDir)[0] : undefined;
  if (!versao) throw new Error("Forge não instalado. Rode server\\instalar-servidor.bat uma vez.");
  const arquivo = process.platform === "win32" ? "win_args.txt" : "unix_args.txt";
  return [`@libraries/net/minecraftforge/forge/${versao}/${arquivo}`];
}

/** Copia o core mod compilado mais recente para server/mods (como o iniciar.bat faz). */
function atualizarCoreMod(pasta: string, log: (t: string) => void) {
  const libs = path.resolve(pasta, "..", "core-mod", "build", "libs");
  const mods = path.join(pasta, "mods");
  if (!fs.existsSync(libs)) return;
  fs.mkdirSync(mods, { recursive: true });
  for (const antigo of fs.readdirSync(mods).filter((n) => /^frxworld-.*\.jar$/.test(n))) {
    fs.rmSync(path.join(mods, antigo));
  }
  for (const jar of fs.readdirSync(libs).filter((n) => /^frxworld-.*\.jar$/.test(n))) {
    fs.copyFileSync(path.join(libs, jar), path.join(mods, jar));
    log(`[painel] Core mod atualizado: ${jar}`);
  }
}

function portaOcupada(porta: number): Promise<boolean> {
  return new Promise((resolve) => {
    const s = net.connect({ port: porta, host: "127.0.0.1" });
    s.once("connect", () => { s.destroy(); resolve(true); });
    s.once("error", () => resolve(false));
    s.setTimeout(800, () => { s.destroy(); resolve(false); });
  });
}

/** Arquivo de comandos que o core mod lê (uma linha por comando). */
function arquivoComandos() { return path.join(pastaServidor(), "frx_comandos.txt"); }

class GerenciadorServidor extends EventEmitter {
  private proc: ChildProcessWithoutNullStreams | null = null;
  /** true = o servidor foi encontrado rodando (não é filho deste painel): comandos vão pelo arquivo. */
  private adotado = false;
  private leitorLog: NodeJS.Timeout | null = null;
  private vigiaPorta: NodeJS.Timeout | null = null;
  private reiniciarDepois = false;
  private timerForcar: NodeJS.Timeout | null = null;
  estado: EstadoServidor = "parado";
  linhas: string[] = [];
  jogadores = new Set<string>();
  iniciadoEm: number | null = null;

  private log(texto: string) {
    const limpo = texto.replace(ANSI, "");
    this.linhas.push(limpo);
    if (this.linhas.length > MAX_LINHAS) this.linhas.splice(0, this.linhas.length - MAX_LINHAS);
    this.emit("evento", { tipo: "linha", texto: limpo } satisfies EventoServidor);
    this.lerLinha(limpo);
  }

  private lerLinha(l: string) {
    if (this.estado === "iniciando" && /Done \([\d.,]+s\)!/.test(l)) this.mudarEstado("online");
    const entrou = l.match(/: (\w{3,16}) joined the game/);
    if (entrou) { this.jogadores.add(entrou[1]); this.mudarEstado(this.estado); }
    const saiu = l.match(/: (\w{3,16}) left the game/);
    if (saiu) { this.jogadores.delete(saiu[1]); this.mudarEstado(this.estado); }
  }

  private mudarEstado(estado: EstadoServidor) {
    this.estado = estado;
    this.emit("evento", this.resumo());
  }

  resumo(): EventoServidor {
    return { tipo: "estado", estado: this.estado, jogadores: [...this.jogadores], iniciadoEm: this.iniciadoEm };
  }

  /** Lê o fim do logs/latest.log de tempos em tempos (servidor adotado: o console dele não chega até aqui). */
  private seguirLog() {
    const arq = path.join(pastaServidor(), "logs", "latest.log");
    let pos = 0;
    try { pos = fs.statSync(arq).size; } catch { /* ainda não existe */ }
    let resto = "";
    if (this.leitorLog) clearInterval(this.leitorLog);
    this.leitorLog = setInterval(() => {
      try {
        const tam = fs.statSync(arq).size;
        if (tam < pos) pos = 0;   // arquivo novo (servidor religou)
        if (tam === pos) return;
        const fd = fs.openSync(arq, "r");
        const buf = Buffer.alloc(Math.min(tam - pos, 1_000_000));
        fs.readSync(fd, buf, 0, buf.length, pos);
        fs.closeSync(fd);
        pos += buf.length;
        const partes = (resto + buf.toString("utf8")).split(/\r?\n/);
        resto = partes.pop() ?? "";
        for (const l of partes) if (l.trim()) this.log(l);
      } catch { /* arquivo ocupado: tenta de novo */ }
    }, 700);
  }

  /** Servidor adotado: quando a porta fechar, ele parou. */
  private vigiarPorta() {
    if (this.vigiaPorta) clearInterval(this.vigiaPorta);
    this.vigiaPorta = setInterval(async () => {
      if (!this.adotado) return;
      if (await portaOcupada(25565)) return;
      this.encerrou("adotado");
    }, 3000);
  }

  private encerrou(codigo: string) {
    this.log(`[painel] Servidor encerrado (${codigo}).`);
    this.proc = null;
    this.adotado = false;
    if (this.leitorLog) { clearInterval(this.leitorLog); this.leitorLog = null; }
    if (this.vigiaPorta) { clearInterval(this.vigiaPorta); this.vigiaPorta = null; }
    this.iniciadoEm = null;
    this.jogadores.clear();
    if (this.timerForcar) clearTimeout(this.timerForcar);
    this.mudarEstado("parado");
    if (this.reiniciarDepois) {
      this.reiniciarDepois = false;
      this.iniciar().catch((e) => this.log(`[painel] ${e.message}`));
    }
  }

  /** Assume o servidor que já está rodando (o painel anterior caiu e o jogo ficou no ar). */
  adotar() {
    if (this.proc || this.adotado) return;
    this.adotado = true;
    this.linhas = [];
    this.jogadores.clear();
    this.iniciadoEm = Date.now();
    this.log("[painel] Servidor já estava rodando: o painel voltou e assumiu o controle (console pelo latest.log).");
    this.mudarEstado("online");
    this.seguirLog();
    this.vigiarPorta();
    try { fs.appendFileSync(arquivoComandos(), "list\n"); } catch { /* sem pasta */ }
  }

  async iniciar() {
    if (this.proc || this.adotado) throw new Error("O servidor já está rodando.");
    if (await portaOcupada(25565)) { this.adotar(); return; }
    const pasta = pastaServidor();
    if (!fs.existsSync(path.join(pasta, "eula.txt"))) {
      throw new Error("Rode server\\instalar-servidor.bat uma vez e aceite o EULA.");
    }

    this.linhas = [];
    this.jogadores.clear();
    this.mudarEstado("iniciando");
    this.iniciadoEm = Date.now();

    try {
      atualizarCoreMod(pasta, (t) => this.log(t));
      const java = acharJava17();
      this.log(`[painel] Iniciando com ${java}`);
      const proc = spawn(
        java,
        ["-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8", "@user_jvm_args.txt", ...argsForge(pasta), "nogui"],
        // separado do painel: se o painel cair, o jogo continua; o painel novo adota pelo latest.log e pelo frx_comandos.txt
        { cwd: pasta, windowsHide: true, detached: true },
      );
      this.proc = proc;

      let resto = "";
      const ler = (dados: Buffer) => {
        const partes = (resto + dados.toString("utf8")).split(/\r?\n/);
        resto = partes.pop() ?? "";
        for (const p of partes) if (p.trim()) this.log(p);
      };
      proc.stdout.on("data", ler);
      proc.stderr.on("data", ler);

      proc.on("error", (e) => this.log(`[painel] Erro ao iniciar o Java: ${e.message}`));
      proc.on("exit", (codigo) => {
        if (resto.trim()) this.log(resto);
        this.encerrou(`código ${codigo ?? "?"}`);
      });
    } catch (e) {
      this.proc = null;
      this.iniciadoEm = null;
      this.mudarEstado("parado");
      throw e;
    }
  }

  /** Para com segurança: manda "stop" (salva o mundo). Se travar, força depois de 90 s. */
  parar() {
    if (!this.proc && !this.adotado) throw new Error("O servidor não está rodando.");
    if (this.estado === "parando") return;
    this.mudarEstado("parando");
    this.comando("stop");
    this.timerForcar = setTimeout(() => {
      this.log("[painel] O servidor não fechou em 90 s; encerrando à força.");
      if (this.proc) this.proc.kill();
      else this.log("[painel] Servidor adotado: o desligar-frx.bat encerra o processo se ele continuar preso.");
    }, 90_000);
  }

  async reiniciar() {
    if (!this.proc && !this.adotado) return this.iniciar();
    this.reiniciarDepois = true;
    this.parar();
  }

  comando(texto: string) {
    if (!this.proc && !this.adotado) throw new Error("O servidor não está rodando.");
    const cmd = texto.replace(/^\//, "").replace(/[\r\n]+/g, " ").trim();
    if (!cmd) return;
    this.log(`> ${cmd}`);
    if (this.proc) this.proc.stdin.write(cmd + "\n");
    else fs.appendFileSync(arquivoComandos(), cmd + "\n");
  }
}

// A janela do painel já recebeu Ctrl+C/Ctrl+Break que derrubava tudo junto (quedas "silenciosas" com o cmd vivo).
// O painel ignora esses sinais e registra no log; para desligar de verdade use o desligar-frx.bat.
const gs = globalThis as unknown as { frxSinais?: boolean };
if (!gs.frxSinais) {
  gs.frxSinais = true;
  for (const sinal of ["SIGINT", "SIGBREAK", "SIGHUP"] as const) {
    try {
      process.on(sinal, () => console.log(`[painel] ${new Date().toLocaleString("pt-BR")} recebi ${sinal} na janela do painel: ignorado para não derrubar o servidor.`));
    } catch { /* sinal não existe nesta plataforma */ }
  }
}

/**
 * Reinicia só o painel (site, admin e páginas dos jogadores, que são o mesmo programa) sem desligar o jogo:
 * roda o reiniciar-painel.bat separado deste processo; ele fecha este painel, compila se o código mudou,
 * sobe de novo e o painel novo assume o servidor que continua rodando.
 */
export function reiniciarSoPainel() {
  const bat = path.resolve(process.cwd(), "..", "reiniciar-painel.bat");
  if (!fs.existsSync(bat)) throw new Error("Não achei o reiniciar-painel.bat na pasta do FRX-WORLD.");
  const p = spawn("cmd.exe", ["/c", bat], { cwd: path.dirname(bat), detached: true, stdio: "ignore", windowsHide: true });
  p.unref();
}

// Uma instância só, mesmo com o recarregamento do `next dev`
const g = globalThis as unknown as { servidorMc?: GerenciadorServidor };
export const servidorMc = g.servidorMc ?? (g.servidorMc = new GerenciadorServidor());
servidorMc.setMaxListeners(50);

// painel novo com o jogo já no ar (o anterior caiu): assume na hora, sem esperar ninguém pedir
const ga = globalThis as unknown as { frxAdocao?: boolean };
if (!ga.frxAdocao) {
  ga.frxAdocao = true;
  setTimeout(() => {
    if (servidorMc.estado !== "parado") return;
    portaOcupada(25565).then((ocupada) => { if (ocupada && servidorMc.estado === "parado") servidorMc.adotar(); }).catch(() => {});
  }, 1500);
}
