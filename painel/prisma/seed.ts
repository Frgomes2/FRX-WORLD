import "dotenv/config";
import bcrypt from "bcryptjs";
import { prisma } from "../src/lib/prisma";

async function main() {
  const email = process.env.ADMIN_EMAIL;
  const senha = process.env.ADMIN_SENHA;
  if (!email || !senha) throw new Error("Defina ADMIN_EMAIL e ADMIN_SENHA no .env");
  const senhaHash = await bcrypt.hash(senha, 12);
  await prisma.admin.upsert({
    where: { email },
    update: { senhaHash },
    create: { email, nome: process.env.ADMIN_NOME ?? "Administrador", senhaHash },
  });
  console.log(`Admin pronto: ${email}`);
}

main().finally(() => prisma.$disconnect());
