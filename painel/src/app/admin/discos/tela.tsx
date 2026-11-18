"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { CampoMoedas, Moedas } from "@/components/itens";
import { ARTES_DISCO, ESTILOS_DISCO, TAMANHO_MAX_DISCO, tempo } from "@/lib/discos-arte";
import { EditorPixel } from "../codex/pixel";
import { excluirDisco } from "./actions";

type Disco = {
  id: string; codigo: string; nome: string; artista: string; estilo: string; descricao: string; preco: number; rankMin: number; limite: number; emitidas: number; arte: number; textura: string;
  duracao: number; canais: number; tamanho: number; ativo: boolean; ordem: number; temArquivo: boolean;
};
const RANKS = ["F", "E", "D", "C", "B", "A", "S"];
const NOVO: Disco = { id: "", codigo: "", nome: "", artista: "", estilo: "", descricao: "", preco: 100, rankMin: 0, limite: 0, emitidas: 0, arte: 1, textura: "", duracao: 0, canais: 0, tamanho: 0, ativo: true, ordem: 0, temArquivo: false };

/** Desenha um disco 16×16 na cor escolhida (vinil escuro, rótulo colorido) e devolve o PNG em base64. */
function discoNaCor(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
  const cv = document.createElement("canvas");
  cv.width = 16; cv.height = 16;
  const ctx = cv.getContext("2d")!;
  const im = ctx.createImageData(16, 16);
  const por = (x: number, y: number, c: [number, number, number]) => { const i = (y * 16 + x) * 4; im.data[i] = c[0]; im.data[i + 1] = c[1]; im.data[i + 2] = c[2]; im.data[i + 3] = 255; };
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x - 7.5, dy = y - 7.5, d = Math.sqrt(dx * dx + dy * dy);
    if (d > 7.3) continue;
    const luz = dx + dy < 0;   // metade de cima e da esquerda mais clara
    if (d <= 0.9) por(x, y, [10, 10, 12]);
    else if (d <= 3.3) por(x, y, luz ? [Math.min(255, r + 30), Math.min(255, g + 30), Math.min(255, b + 30)] : [Math.round(r * 0.72), Math.round(g * 0.72), Math.round(b * 0.72)]);
    else if (d > 6.5) por(x, y, [12, 12, 15]);
    else if (d > 4.7 && d <= 5.5) por(x, y, luz ? [70, 70, 80] : [44, 44, 52]);
    else por(x, y, luz ? [40, 40, 47] : [26, 26, 31]);
  }
  ctx.putImageData(im, 0, 0);
  return cv.toDataURL("image/png").replace(/^data:image\/png;base64,/, "");
}

function Bolacha({ arte, textura = "", tam = 34 }: { arte: number; textura?: string; tam?: number }) {
  if (textura) return <img src={`data:image/png;base64,${textura}`} alt="" width={tam} height={tam} className="shrink-0" style={{ imageRendering: "pixelated" }} />;
  const cor = ARTES_DISCO.find((a) => a.id === arte)?.cor ?? "#d9b23c";
  return (
    <span className="inline-grid shrink-0 place-items-center rounded-full border-2 border-black bg-neutral-900" style={{ width: tam, height: tam }}>
      <span className="rounded-full" style={{ width: tam * 0.42, height: tam * 0.42, background: cor }} />
    </span>
  );
}

function Formulario({ inicial, fechar, estilos }: { inicial: Disco; fechar: () => void; estilos: string[] }) {
  const router = useRouter();
  const [d, setD] = useState(inicial);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [corDisco, setCorDisco] = useState("#d4a93a");
  const [editorVersao, setEditorVersao] = useState(0);   // muda quando o desenho é trocado de fora, para o editor recarregar
  const mudar = <K extends keyof Disco>(k: K, v: Disco[K]) => setD((x) => ({ ...x, [k]: v }));
  const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 text-sm outline-none focus:border-ouro";
  const rotulo = "mb-1 block text-xs uppercase tracking-wider text-pedra-500";

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    setErro("");
    if (arquivo && arquivo.size > TAMANHO_MAX_DISCO) { setErro("O arquivo passa de 9 MB. Exporte com qualidade menor (96 kbps, mono)."); return; }
    if (!d.id && !arquivo) { setErro("Escolha o arquivo .ogg da música."); return; }
    const f = new FormData();
    for (const k of ["id", "nome", "artista", "estilo", "descricao", "preco", "rankMin", "limite", "arte", "ordem"] as const) f.set(k, String(d[k]));
    f.set("ativo", d.ativo ? "1" : "0");
    f.set("textura", d.textura);
    if (arquivo) f.set("arquivo", arquivo);
    setEnviando(true);
    try {
      const r = await fetch("/admin/discos/enviar", { method: "POST", body: f });
      const j = await r.json().catch(() => ({ erro: "O painel não respondeu. O arquivo pode ser grande demais." }));
      if (!r.ok || j.erro) { setErro(j.erro ?? "Não consegui salvar."); return; }
      if (j.aviso) window.alert(j.aviso);
      router.refresh();
      fechar();
    } catch {
      setErro("Não consegui enviar. Confira a conexão e o tamanho do arquivo.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={salvar} className="mb-8 rounded-xl border border-ouro-escuro bg-pedra-900 p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-titulo text-2xl text-ouro">{d.id ? `Editar ${inicial.nome}` : "Novo disco"}</h2>
        <button type="button" onClick={fechar} className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">Fechar</button>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="block"><span className={rotulo}>Nome do disco</span>
          <input className={campo} value={d.nome} maxLength={40} onChange={(e) => mudar("nome", e.target.value)} placeholder="Ex.: Canção da Taverna" required /></label>
        <label className="block"><span className={rotulo}>Artista (opcional)</span>
          <input className={campo} value={d.artista} maxLength={40} onChange={(e) => mudar("artista", e.target.value)} /></label>
        <label className="block"><span className={rotulo}>Estilo de música</span>
          <input className={campo} value={d.estilo} maxLength={24} list="estilos-disco" onChange={(e) => mudar("estilo", e.target.value)} placeholder="Ex.: Rock" />
          <datalist id="estilos-disco">{estilos.map((e) => <option key={e} value={e} />)}</datalist></label>
      </div>
      <p className="mt-1 text-xs text-pedra-500">O estilo vira um filtro na loja do Mercador de Discos. Escolha da lista ou escreva outro; use sempre a mesma grafia para o mesmo estilo.</p>
      <label className="mt-3 block"><span className={rotulo}>Descrição (aparece no disco)</span>
        <input className={campo} value={d.descricao} maxLength={160} onChange={(e) => mudar("descricao", e.target.value)} /></label>

      <div className="mt-3 rounded-lg border border-pedra-700 p-3">
        <span className={rotulo}>Música (.ogg, até 9 MB){d.id ? " — deixe vazio para manter a atual" : ""}</span>
        <input type="file" accept=".ogg,audio/ogg" onChange={(e) => setArquivo(e.target.files?.[0] ?? null)} className="block w-full text-sm" />
        {arquivo && <p className="mt-1 text-xs text-pedra-500">{arquivo.name} · {(arquivo.size / 1048576).toFixed(1)} MB</p>}
        {d.id && d.temArquivo && !arquivo && (
          <div className="mt-2">
            <p className="mb-1 text-xs text-pedra-500">Atual: {tempo(d.duracao)} · {d.canais === 1 ? "mono" : "estéreo"} · {(d.tamanho / 1048576).toFixed(1)} MB</p>
            <audio controls preload="none" src={`/admin/discos/enviar?codigo=${d.codigo}`} className="w-full" />
          </div>
        )}
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div><span className={rotulo}>Preço</span><CampoMoedas cobre={d.preco} onChange={(c) => mudar("preco", c)} /></div>
        <label className="block"><span className={rotulo}>Rank da Guilda para comprar</span>
          <select className={campo} value={d.rankMin} onChange={(e) => mudar("rankMin", Number(e.target.value))}>
            {RANKS.map((r, i) => <option key={r} value={i}>{i === 0 ? "Qualquer um (F)" : `Rank ${r}`}</option>)}
          </select></label>
        <label className="block"><span className={rotulo}>Ordem na loja</span>
          <input type="number" min={0} max={9999} className={campo} value={d.ordem} onChange={(e) => mudar("ordem", Number(e.target.value) || 0)} /></label>
        <label className="block"><span className={rotulo}>Unidades no mundo (0 = sem limite)</span>
          <input type="number" min={0} max={100000} className={campo} value={d.limite} onChange={(e) => mudar("limite", Math.max(0, Math.floor(Number(e.target.value) || 0)))} /></label>
      </div>
      <p className="mt-1 text-xs text-pedra-500">
        {d.limite > 0
          ? `Disco raro: o mercador vende só ${d.limite} unidades e depois mostra "esgotado". Já vendidas: ${d.emitidas}. Cada disco sai numerado (nº 1 de ${d.limite}). Um disco destruído ou perdido não volta para a loja.`
          : `Sem limite: o mercador vende quantas quiserem. Já vendidas: ${d.emitidas}.`}
      </p>

      <div className="mt-3 rounded-lg border border-pedra-700 p-3">
        <span className={rotulo}>Aparência do disco</span>
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <Bolacha arte={d.arte} textura={d.textura} tam={48} />
          <span className="text-sm text-pedra-500">{d.textura ? "Desenho próprio" : "Aparência pronta do jogo"}</span>
          {d.textura && <button type="button" onClick={() => { mudar("textura", ""); setEditorVersao((v) => v + 1); }} className="rounded-md border border-pedra-700 px-2 py-1 text-xs hover:border-ouro">Voltar para uma aparência pronta</button>}
        </div>
        <p className="mb-1 text-sm"><b>1. Escolher uma cor</b> <span className="text-pedra-500">— o painel desenha o disco nessa cor</span></p>
        <div className="mb-4 flex items-center gap-2">
          <input type="color" value={corDisco} onChange={(e) => setCorDisco(e.target.value)} className="h-9 w-14 cursor-pointer rounded border border-pedra-700 bg-pedra-950" />
          <button type="button" onClick={() => { mudar("textura", discoNaCor(corDisco)); setEditorVersao((v) => v + 1); }} className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">Gerar disco nessa cor</button>
        </div>
        <p className="mb-1 text-sm"><b>2. Enviar uma imagem ou desenhar</b> <span className="text-pedra-500">— PNG quadrado (16×16 ou 32×32) pelo botão "Importar PNG", ou pinte na grade. Dá para gerar pela cor e retocar aqui.</span></p>
        <EditorPixel key={editorVersao} valor={d.textura} aoMudar={(png) => mudar("textura", png)} itemBase="" />
        <p className="mb-1 mt-4 text-sm"><b>3. Aparências prontas do jogo</b> <span className="text-pedra-500">— valem quando não há desenho próprio</span></p>
        <div className="flex flex-wrap gap-2">
          {ARTES_DISCO.map((a) => (
            <button type="button" key={a.id} onClick={() => { mudar("arte", a.id); if (d.textura) { mudar("textura", ""); setEditorVersao((v) => v + 1); } }} title={a.nome}
              className={`flex items-center gap-2 rounded-lg border px-2 py-1.5 text-xs ${d.arte === a.id && !d.textura ? "border-ouro text-ouro" : "border-pedra-700 hover:border-ouro"}`}>
              <Bolacha arte={a.id} tam={24} />{a.nome}
            </button>
          ))}
        </div>
      </div>

      <label className="mt-4 flex items-center gap-2 text-sm"><input type="checkbox" checked={d.ativo} onChange={(e) => mudar("ativo", e.target.checked)} /> À venda</label>
      {erro && <p className="mt-3 rounded-md border border-red-800 bg-red-950/40 px-3 py-2 text-sm text-red-300">{erro}</p>}
      <div className="mt-4 flex items-center gap-3">
        <button disabled={enviando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:brightness-110 disabled:opacity-60">{enviando ? "Enviando…" : "Salvar disco"}</button>
        <span className="text-xs text-pedra-500">O disco aparece no vendedor em até 1 minuto. Quem está no jogo recebe o pacote novo com a música.</span>
      </div>
    </form>
  );
}

export function TelaDiscos({ discos }: { discos: Disco[] }) {
  const [editando, setEditando] = useState<Disco | null>(null);
  const [filtro, setFiltro] = useState("");
  // sugestões: os estilos já usados primeiro, depois os da lista pronta
  const usados = [...new Set(discos.map((d) => d.estilo).filter(Boolean))].sort((a, b) => a.localeCompare(b, "pt-BR"));
  const estilos = [...usados, ...ESTILOS_DISCO.filter((e) => !usados.includes(e))];
  const mostrados = filtro ? discos.filter((d) => d.estilo === filtro) : discos;
  const [, iniciar] = useTransition();
  return (
    <>
      {editando ? <Formulario key={editando.id || "novo"} inicial={editando} fechar={() => setEditando(null)} estilos={estilos} />
        : <button onClick={() => setEditando(NOVO)} className="mb-5 rounded-md bg-ouro px-4 py-2 font-semibold text-pedra-950 hover:brightness-110">+ Novo disco</button>}
      {usados.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2 text-sm">
          <span className="text-pedra-500">Estilo:</span>
          {["", ...usados].map((e) => (
            <button key={e || "todos"} onClick={() => setFiltro(e)} className={`rounded-full border px-3 py-1 ${filtro === e ? "border-ouro text-ouro" : "border-pedra-700 hover:border-ouro"}`}>
              {e || "Todos"} <span className="text-pedra-500">{e ? discos.filter((d) => d.estilo === e).length : discos.length}</span>
            </button>
          ))}
        </div>
      )}
      {discos.length === 0 ? <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-6 text-pedra-500">Nenhum disco cadastrado ainda.</p> : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {mostrados.map((d) => (
            <div key={d.id} className={`rounded-xl border bg-pedra-900 p-4 ${d.ativo && d.temArquivo ? "border-pedra-700" : "border-pedra-800 opacity-70"}`}>
              <div className="flex items-center gap-3">
                <Bolacha arte={d.arte} textura={d.textura} tam={40} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-titulo text-lg text-ouro">{d.nome}</p>
                  <p className="truncate text-xs text-pedra-500">{d.estilo ? `${d.estilo} · ` : ""}{d.artista || "sem artista"} · {tempo(d.duracao)} · {d.canais === 1 ? "mono" : "estéreo"} · {(d.tamanho / 1048576).toFixed(1)} MB</p>
                </div>
              </div>
              <div className="mt-3 flex items-center justify-between text-sm">
                <Moedas cobre={d.preco} />
                <span className="text-xs text-pedra-500">{d.rankMin > 0 ? `Rank ${RANKS[d.rankMin]}+` : "todos"} · {d.limite > 0 ? `${Math.min(d.emitidas, d.limite)} de ${d.limite} no mundo${d.emitidas >= d.limite ? " (esgotado)" : ""}` : `${d.emitidas} vendidos`} · {!d.temArquivo ? "SEM ÁUDIO" : d.ativo ? "à venda" : "fora da loja"}</span>
              </div>
              <div className="mt-3 flex gap-2">
                <button onClick={() => setEditando(d)} className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">Editar</button>
                <button onClick={() => { if (window.confirm(`Excluir o disco "${d.nome}" e o arquivo da música?`)) iniciar(() => excluirDisco(d.id)); }}
                  className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm text-red-300 hover:border-red-500">Excluir</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
