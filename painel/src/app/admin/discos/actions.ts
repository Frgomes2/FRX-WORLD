"use server";

import fs from "fs";
import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { arquivoDoDisco } from "@/lib/discos";

export async function excluirDisco(id: string) {
  if (!(await auth())?.user) throw new Error("Não autorizado");
  const d = await prisma.disco.findUnique({ where: { id } });
  if (!d) return;
  await prisma.disco.delete({ where: { id } });
  try { fs.unlinkSync(arquivoDoDisco(d.codigo)); } catch { /* o arquivo já não estava lá */ }
  revalidatePath("/admin/discos");
}
