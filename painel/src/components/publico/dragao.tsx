/**
 * Dragão do FRX-WORLD (desenho próprio, em silhueta) cruzando o fundo do site: vai, vira e volta.
 * Fica atrás do conteúdo e não recebe cliques. A animação está em globals.css (.frx-dragao*).
 */
export function Dragao() {
  return (
    <div className="frx-dragao-ceu" aria-hidden="true">
      <div className="frx-dragao-voo">
        <svg className="frx-dragao" viewBox="0 0 400 220" width="400" height="220">
          <defs>
            <linearGradient id="frx-dragao-cor" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="#f0c75a" /><stop offset="1" stopColor="#8a4fd6" />
            </linearGradient>
          </defs>
          <g fill="url(#frx-dragao-cor)">
            {/* asa de trás */}
            <path className="frx-asa frx-asa-tras" d="M246 106 L232 34 L150 6 Q180 32 166 56 Q196 58 190 84 Q216 82 216 106 Z" opacity="0.5" />
            {/* cauda, corpo, pescoço e cabeça */}
            <path d="M8 118 L30 104 L26 116 Q70 96 120 110 Q170 92 230 104 Q270 108 300 92 Q318 80 338 82 L350 66 L352 82 L366 72 L362 88 Q384 92 392 104 Q376 112 356 108 Q336 112 322 122 Q290 144 236 138 Q180 150 124 132 Q76 124 30 134 L34 126 Z" />
            <path d="M348 96 a4 4 0 1 0 0.1 0 Z" fill="#17140f" />
            {/* asa da frente */}
            <path className="frx-asa" d="M228 110 L206 28 L112 4 Q148 32 132 60 Q168 60 160 88 Q192 84 192 110 Z" />
          </g>
        </svg>
      </div>
    </div>
  );
}
