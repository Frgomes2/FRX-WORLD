"use server";

import { revalidatePath } from "next/cache";
import bcrypt from "bcryptjs";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

export type Resultado = { erro?: string; ok?: string };

async function adminAtual() {
  const sessao = await auth();
  const email = sessao?.user?.email;
  if (!email) throw new Error("Não autorizado");
  const admin = await prisma.admin.findUnique({ where: { email } });
  if (!admin) throw new Error("Não autorizado");
  return admin;
}

function senhaFraca(senha: string) {
  if (senha.length < 10) return "A senha precisa ter pelo menos 10 caracteres.";
  if (!/[A-Za-z]/.test(senha) || !/[0-9]/.test(senha)) return "Use letras e números na senha.";
  return null;
}

export async function criarAdmin(_: Resultado, form: FormData): Promise<Resultado> {
  await adminAtual();
  const nome = String(form.get("nome") ?? "").trim();
  const email = String(form.get("email") ?? "").trim().toLowerCase();
  const senha = String(form.get("senha") ?? "");
  if (!nome) return { erro: "Informe o nome." };
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return { erro: "E-mail inválido." };
  const fraca = senhaFraca(senha);
  if (fraca) return { erro: fraca };
  if (await prisma.admin.findUnique({ where: { email } })) return { erro: "Já existe um admin com esse e-mail." };
  await prisma.admin.create({ data: { nome, email, senhaHash: await bcrypt.hash(senha, 12) } });
  revalidatePath("/admins");
  return { ok: `${nome} agora é admin. Passe o e-mail e a senha para ele.` };
}

export async function removerAdmin(form: FormData) {
  const eu = await adminAtual();
  const id = String(form.get("id") ?? "");
  if (id === eu.id) throw new Error("Você não pode remover a si mesmo.");
  if ((await prisma.admin.count()) <= 1) throw new Error("O painel precisa de pelo menos um admin.");
  await prisma.admin.delete({ where: { id } });
  revalidatePath("/admins");
}

export async function trocarSenha(_: Resultado, form: FormData): Promise<Resultado> {
  const eu = await adminAtual();
  const atual = String(form.get("atual") ?? "");
  const nova = String(form.get("nova") ?? "");
  if (!(await bcrypt.compare(atual, eu.senhaHash))) return { erro: "A senha atual não confere." };
  const fraca = senhaFraca(nova);
  if (fraca) return { erro: fraca };
  await prisma.admin.update({ where: { id: eu.id }, data: { senhaHash: await bcrypt.hash(nova, 12) } });
  return { ok: "Senha alterada." };
}
