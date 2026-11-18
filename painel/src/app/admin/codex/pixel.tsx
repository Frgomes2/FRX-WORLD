"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const PALETA = ["#000000", "#3b3b3b", "#7a7a7a", "#c8c8c8", "#ffffff", "#5a2f0e", "#8a5a2b", "#c89a5a", "#7a0f0f", "#d43a2f", "#ff8a3d", "#ffd84a",
  "#2f6b2a", "#5bc25b", "#1f4f8f", "#4aa3ff", "#7ae0ff", "#4b2a8f", "#b085f5", "#ff7ad9", "#3a2a18", "#d4a93a", "#9be5d0", "#101830"];
type Ferramenta = "lapis" | "borracha" | "balde" | "gotas";

function hexParaRgba(h: string): [number, number, number, number] {
  return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
}
function rgbaParaHex(d: Uint8ClampedArray, i: number) {
  return "#" + [d[i], d[i + 1], d[i + 2]].map((v) => v.toString(16).padStart(2, "0")).join("");
}

/**
 * Desenho da arma em pixels (16×16 ou 32×32), como as texturas do jogo. Dá para desenhar aqui, partir do item base
 * ou importar um PNG pronto. O resultado sai como PNG em base64 por aoMudar ("" = usar a aparência do item base).
 */
export function EditorPixel({ valor, aoMudar, itemBase }: { valor: string; aoMudar: (png: string) => void; itemBase: string }) {
  const [tam, setTam] = useState(16);
  const [px, setPx] = useState<Uint8ClampedArray>(() => new Uint8ClampedArray(16 * 16 * 4));
  const [cor, setCor] = useState("#d4a93a");
  const [ferramenta, setFerramenta] = useState<Ferramenta>("lapis");
  const [aviso, setAviso] = useState("");
  const historico = useRef<{ tam: number; px: Uint8ClampedArray }[]>([]);
  const tela = useRef<HTMLCanvasElement>(null);
  const previa = useRef<HTMLCanvasElement>(null);
  const desenhando = useRef(false);
  const ZOOM = tam === 16 ? 20 : 10;

  const exportar = useCallback((t: number, dados: Uint8ClampedArray) => {
    let vazio = true;
    for (let i = 3; i < dados.length; i += 4) if (dados[i] > 0) { vazio = false; break; }
    if (vazio) { aoMudar(""); return; }
    const cv = document.createElement("canvas");
    cv.width = t; cv.height = t;
    cv.getContext("2d")!.putImageData(new ImageData(new Uint8ClampedArray(dados), t, t), 0, 0);
    aoMudar(cv.toDataURL("image/png").replace(/^data:image\/png;base64,/, ""));
  }, [aoMudar]);

  const aplicar = useCallback((t: number, dados: Uint8ClampedArray, guardar = true) => {
    if (guardar) { historico.current.push({ tam, px }); if (historico.current.length > 40) historico.current.shift(); }
    setTam(t); setPx(dados); exportar(t, dados);
  }, [tam, px, exportar]);

  /** Carrega uma imagem (item base, PNG importado ou a textura salva) para a grade, sem suavizar. */
  const carregarImagem = useCallback((src: string, guardar: boolean, forcarTam?: number) => {
    const im = new Image();
    im.onload = () => {
      if (im.width !== im.height) { setAviso("A imagem precisa ser quadrada (16×16 ou 32×32)."); return; }
      const t = forcarTam ?? (im.width >= 32 ? 32 : 16);
      const cv = document.createElement("canvas");
      cv.width = t; cv.height = t;
      const ctx = cv.getContext("2d")!;
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(im, 0, 0, t, t);
      setAviso(im.width > 32 ? `A imagem tinha ${im.width}×${im.width} e foi reduzida para ${t}×${t}.` : "");
      aplicar(t, ctx.getImageData(0, 0, t, t).data, guardar);
    };
    im.onerror = () => setAviso("Não consegui abrir essa imagem.");
    im.src = src;
  }, [aplicar]);

  // textura já salva
  useEffect(() => { if (valor) carregarImagem(`data:image/png;base64,${valor}`, false); /* só ao abrir */ // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // redesenha a grade e a prévia
  useEffect(() => {
    for (const [cv, z, grade] of [[tela.current, ZOOM, true], [previa.current, 64 / tam, false]] as [HTMLCanvasElement | null, number, boolean][]) {
      if (!cv) continue;
      cv.width = tam * z; cv.height = tam * z;
      const ctx = cv.getContext("2d")!;
      for (let y = 0; y < tam; y++) for (let x = 0; x < tam; x++) {
        const i = (y * tam + x) * 4;
        if (grade) { ctx.fillStyle = (x + y) % 2 ? "#2a241c" : "#332b22"; ctx.fillRect(x * z, y * z, z, z); }
        if (px[i + 3] > 0) { ctx.fillStyle = `rgba(${px[i]},${px[i + 1]},${px[i + 2]},${px[i + 3] / 255})`; ctx.fillRect(x * z, y * z, z, z); }
      }
    }
  }, [px, tam, ZOOM]);

  function pintar(e: React.PointerEvent<HTMLCanvasElement>, inicio: boolean) {
    const r = e.currentTarget.getBoundingClientRect();
    const x = Math.floor(((e.clientX - r.left) / r.width) * tam), y = Math.floor(((e.clientY - r.top) / r.height) * tam);
    if (x < 0 || y < 0 || x >= tam || y >= tam) return;
    const i = (y * tam + x) * 4;
    if (ferramenta === "gotas") { if (px[i + 3] > 0) setCor(rgbaParaHex(px, i)); setFerramenta("lapis"); return; }
    const novo = new Uint8ClampedArray(px);
    const alvo = ferramenta === "borracha" ? [0, 0, 0, 0] : hexParaRgba(cor);
    if (ferramenta === "balde") {
      if (!inicio) return;
      const de = [px[i], px[i + 1], px[i + 2], px[i + 3]];
      if (de.every((v, k) => v === alvo[k])) return;
      const fila = [[x, y]];
      while (fila.length) {
        const [cx, cy] = fila.pop()!;
        if (cx < 0 || cy < 0 || cx >= tam || cy >= tam) continue;
        const j = (cy * tam + cx) * 4;
        if (novo[j] !== de[0] || novo[j + 1] !== de[1] || novo[j + 2] !== de[2] || novo[j + 3] !== de[3]) continue;
        novo.set(alvo, j);
        fila.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
      }
    } else {
      if ([0, 1, 2, 3].every((k) => px[i + k] === alvo[k])) return;
      novo.set(alvo, i);
    }
    aplicar(tam, novo, inicio);
  }

  const botao = (ativo: boolean) => `rounded-md border px-2.5 py-1.5 text-xs ${ativo ? "border-ouro bg-pedra-700 text-ouro" : "border-pedra-700 hover:border-ouro-escuro"}`;
  return (
    <div className="rounded-xl border border-pedra-700 bg-pedra-950 p-3">
      <div className="mb-2 flex flex-wrap items-center gap-1.5">
        {([["lapis", "✏️ Lápis"], ["borracha", "🧽 Borracha"], ["balde", "🪣 Balde"], ["gotas", "💧 Pegar cor"]] as [Ferramenta, string][]).map(([f, r]) => (
          <button key={f} type="button" className={botao(ferramenta === f)} onClick={() => setFerramenta(f)}>{r}</button>
        ))}
        <button type="button" className={botao(false)} disabled={!historico.current.length}
          onClick={() => { const h = historico.current.pop(); if (h) { setTam(h.tam); setPx(h.px); exportar(h.tam, h.px); } }}>↶ Desfazer</button>
        <span className="mx-1 h-5 w-px bg-pedra-700" />
        <button type="button" className={botao(false)} disabled={!itemBase} title="Copia o desenho do item base para você modificar"
          onClick={() => carregarImagem(`/api/painel/item-icone?id=${encodeURIComponent(itemBase)}`, true)}>Partir do item base</button>
        <label className={`${botao(false)} cursor-pointer`}>Importar PNG
          <input type="file" accept="image/png" className="hidden" onChange={(e) => {
            const arq = e.target.files?.[0];
            if (arq) { const l = new FileReader(); l.onload = () => carregarImagem(String(l.result), true); l.readAsDataURL(arq); }
            e.target.value = "";
          }} />
        </label>
        <button type="button" className={botao(false)} onClick={() => aplicar(tam, new Uint8ClampedArray(tam * tam * 4))}>Limpar</button>
        <button type="button" className={botao(false)} title="Troca entre 16×16 e 32×32 (redimensiona o desenho)"
          onClick={() => { const cv = document.createElement("canvas"); cv.width = tam; cv.height = tam; cv.getContext("2d")!.putImageData(new ImageData(new Uint8ClampedArray(px), tam, tam), 0, 0); carregarImagem(cv.toDataURL(), true, tam === 16 ? 32 : 16); }}>
          {tam}×{tam} ⇄
        </button>
      </div>
      <div className="flex flex-wrap items-start gap-4">
        <canvas ref={tela} className="touch-none rounded border border-pedra-700" style={{ width: 320, height: 320, imageRendering: "pixelated", cursor: "crosshair" }}
          onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); desenhando.current = true; pintar(e, true); }}
          onPointerMove={(e) => { if (desenhando.current && ferramenta !== "balde") pintar(e, false); }}
          onPointerUp={() => { desenhando.current = false; }} />
        <div className="space-y-3">
          <div className="grid grid-cols-6 gap-1">
            {PALETA.map((c) => (
              <button key={c} type="button" aria-label={`Cor ${c}`} onClick={() => { setCor(c); if (ferramenta !== "balde") setFerramenta("lapis"); }}
                className={`h-6 w-6 rounded border ${cor === c ? "border-ouro ring-1 ring-ouro" : "border-pedra-700"}`} style={{ background: c }} />
            ))}
          </div>
          <label className="flex items-center gap-2 text-xs text-pedra-500">Outra cor
            <input type="color" value={cor} onChange={(e) => setCor(e.target.value)} className="h-7 w-10 cursor-pointer rounded border border-pedra-700 bg-transparent" />
          </label>
          <div>
            <p className="mb-1 text-xs text-pedra-500">Como fica no jogo</p>
            <div className="flex gap-2">
              <canvas ref={previa} className="rounded border border-pedra-700 bg-pedra-800" style={{ width: 64, height: 64, imageRendering: "pixelated" }} />
            </div>
          </div>
        </div>
      </div>
      <p className="mt-2 text-xs text-pedra-500">{aviso || (valor ? "Aparência própria: os jogadores veem este desenho pelo pacote de recursos do servidor." : "Sem desenho: a arma usa a aparência do item base. Desenhe na ponta de cima à direita para a de baixo à esquerda, como as espadas do jogo.")}</p>
    </div>
  );
}
