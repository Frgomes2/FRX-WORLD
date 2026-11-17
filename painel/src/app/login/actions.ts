"use server";

import { AuthError } from "next-auth";
import { signIn } from "@/auth";

export async function entrar(_estado: string | null, form: FormData) {
  try {
    await signIn("credentials", {
      email: form.get("email"),
      senha: form.get("senha"),
      redirectTo: "/admin",
    });
    return null;
  } catch (erro) {
    if (erro instanceof AuthError) return "E-mail ou senha incorretos.";
    throw erro; // redirect do Next precisa subir
  }
}
