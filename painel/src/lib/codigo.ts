import { randomInt } from "crypto";

// Sem caracteres ambíguos (0/O, 1/I/L)
const ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

/** Gera um código de convite no formato FRX-XXXX-XXXX. */
export function gerarCodigo() {
  const bloco = () => Array.from({ length: 4 }, () => ALFABETO[randomInt(ALFABETO.length)]).join("");
  return `FRX-${bloco()}-${bloco()}`;
}
