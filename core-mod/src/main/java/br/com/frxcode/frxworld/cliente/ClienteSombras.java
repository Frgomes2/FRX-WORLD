package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.SyncSombras;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Exército de sombras do jogador (só no cliente). */
public final class ClienteSombras {
    public static List<SyncSombras.Info> lista = new ArrayList<>();
    public static String[] atalhos = {"", "", ""};
    public static int vagas = 0, ativas = 0, ativasMax = 0;
    public static boolean recebido = false;

    private ClienteSombras() {}

    public static void aplicar(SyncSombras msg) {
        lista = msg.lista();
        for (int i = 0; i < atalhos.length; i++) atalhos[i] = i < msg.atalhos().size() ? msg.atalhos().get(i) : "";
        vagas = msg.vagas();
        ativas = msg.ativas();
        ativasMax = msg.ativasMax();
        recebido = true;
        if (Minecraft.getInstance().screen instanceof TelaSombras tela) tela.atualizar();
    }

    public static SyncSombras.Info porId(String id) {
        for (SyncSombras.Info i : lista) if (i.id().equals(id)) return i;
        return null;
    }

    /** Em qual atalho (0..2) esta sombra está; -1 = nenhum. */
    public static int atalhoDe(String id) {
        for (int i = 0; i < atalhos.length; i++) if (atalhos[i].equals(id)) return i;
        return -1;
    }

    public static void limpar() {
        lista = new ArrayList<>();
        atalhos = new String[]{"", "", ""};
        vagas = ativas = ativasMax = 0;
        recebido = false;
    }
}
