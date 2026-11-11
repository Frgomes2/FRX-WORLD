import NextAuth from "next-auth";
import Credentials from "next-auth/providers/credentials";
import bcrypt from "bcryptjs";
import { z } from "zod";
import { prisma } from "@/lib/prisma";

const loginSchema = z.object({
  email: z.string().email(),
  senha: z.string().min(1),
});

// Trava de força bruta: 5 senhas erradas seguidas no mesmo e-mail bloqueiam por 15 min.
const falhas = new Map<string, { n: number; ate: number }>();
const MAX_FALHAS = 5;
const BLOQUEIO_MS = 15 * 60 * 1000;

export const { handlers, auth, signIn, signOut } = NextAuth({
  session: { strategy: "jwt" },
  pages: { signIn: "/login" },
  providers: [
    Credentials({
      credentials: { email: {}, senha: {} },
      async authorize(credentials) {
        const dados = loginSchema.safeParse(credentials);
        if (!dados.success) return null;
        const chave = dados.data.email.trim().toLowerCase();
        const f = falhas.get(chave);
        if (f && f.n >= MAX_FALHAS && f.ate > Date.now()) return null;
        const admin = (await prisma.admin.findUnique({ where: { email: dados.data.email.trim() } }))
          ?? (await prisma.admin.findUnique({ where: { email: chave } }));
        const ok = !!admin && (await bcrypt.compare(dados.data.senha, admin.senhaHash));
        if (!ok || !admin) {
          const n = f && f.ate > Date.now() ? f.n + 1 : 1;
          falhas.set(chave, { n, ate: Date.now() + BLOQUEIO_MS });
          return null;
        }
        falhas.delete(chave);
        return { id: admin.id, email: admin.email, name: admin.nome };
      },
    }),
  ],
});
