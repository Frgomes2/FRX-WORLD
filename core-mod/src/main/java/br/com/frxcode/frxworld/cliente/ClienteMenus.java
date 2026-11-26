package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Menus dos NPCs no cliente (banco e forja) e desenho de moedas. */
public final class ClienteMenus {
    public static final int PERGAMINHO = 0xFFE9DFC8, PERGAMINHO_ESC = 0xFFD8CBAE, TINTA = 0xFF3A2A1A, MOLDURA = 0xFF5A3A1E,
            OURO = 0xFFB8892A, CINZA = 0xFF8A7D6A, VERDE = 0xFF2E7D32, VERMELHO = 0xFF9B2C2C, CAIXA = 0xFFF4EDDC;

    private ClienteMenus() {}

    public static void abrir(AbrirMenu msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.tipo().equals("marcador")) { MarcadorMapa.tratar(msg.dados()); return; }
        if (msg.tipo().equals("x1")) {
            if (msg.dados().getBoolean("fechar")) { if (mc.screen instanceof TelaX1) mc.setScreen(null); return; }   // o duelo começou
            if (mc.screen instanceof TelaX1 t) t.atualizar(msg.dados());
            else if (msg.dados().getBoolean("abrir")) mc.setScreen(new TelaX1(msg.dados()));   // comando /x1
            return;
        }
        if (msg.tipo().equals("personagens")) {
            if (msg.dados().getBoolean("reconectar")) { Reconexao.marcar(msg.dados().getString("nome")); return; }   // troca: o jogo vai entrar de novo
            if (mc.screen instanceof TelaPersonagens t) t.atualizar(msg.dados()); else mc.setScreen(new TelaPersonagens(msg.dados()));
            return;
        }
        if (msg.tipo().equals("area")) { AreasMapa.tratar(msg.dados()); return; }   // área pintada no mapa: não abre tela   // ponto no mapa: não abre tela
        if (msg.tipo().equals("radar")) { mc.setScreen(new TelaRadar(msg.dados())); return; }
        if (msg.tipo().equals("pokebola")) { if (mc.screen instanceof TelaPokebola t) t.atualizar(msg.dados()); else mc.setScreen(new TelaPokebola(msg.dados())); return; }
        if (msg.tipo().equals("bencao")) { if (mc.screen instanceof TelaBencao t) t.atualizar(msg.dados()); else mc.setScreen(new TelaBencao(msg.dados())); return; }
        if (msg.tipo().equals("deathnote")) {
            if (msg.dados().getBoolean("fechar")) { if (mc.screen instanceof TelaDeathNote) mc.setScreen(null); }
            else mc.setScreen(new TelaDeathNote(msg.dados()));
            return;
        }
        if (msg.tipo().equals("shenlong")) {
            if (msg.dados().getBoolean("fechar")) { if (mc.screen instanceof TelaShenlong) mc.setScreen(null); }
            else if (mc.screen instanceof TelaShenlong t) t.atualizar(msg.dados()); else mc.setScreen(new TelaShenlong(msg.dados()));
            return;
        }
        if (msg.tipo().equals("banco")) {
            if (mc.screen instanceof TelaBanco t) t.atualizar(msg.dados()); else mc.setScreen(new TelaBanco(msg.dados()));
        } else if (msg.tipo().equals("abertura")) {
            if (mc.screen instanceof TelaAbertura t) t.atualizar(msg.dados()); else mc.setScreen(new TelaAbertura(msg.dados()));
        } else if (msg.tipo().equals("dialogo")) {
            if (mc.screen instanceof TelaDialogo t) t.atualizar(msg.dados()); else mc.setScreen(new TelaDialogo(msg.dados()));
        } else if (msg.tipo().equals("vender")) {
            if (mc.screen instanceof TelaVender t) t.atualizar(msg.dados()); else mc.setScreen(new TelaVender(msg.dados()));
        } else if (msg.tipo().equals("guilda")) {
            if (mc.screen instanceof TelaGuilda t) t.atualizar(msg.dados()); else mc.setScreen(new TelaGuilda(msg.dados()));
        } else if (msg.tipo().equals("mercado")) {
            if (mc.screen instanceof TelaMercado t) t.atualizar(msg.dados()); else mc.setScreen(new TelaMercado(msg.dados()));
        } else if (msg.tipo().equals("discos")) {
            if (mc.screen instanceof TelaDiscos t) t.atualizar(msg.dados()); else mc.setScreen(new TelaDiscos(msg.dados()));
        } else if (msg.tipo().equals("clas")) {
            if (mc.screen instanceof TelaClas t) t.atualizar(msg.dados()); else mc.setScreen(new TelaClas(msg.dados()));
        } else if (msg.tipo().equals("escravos")) {
            if (mc.screen instanceof TelaEscravos t) t.atualizar(msg.dados()); else mc.setScreen(new TelaEscravos(msg.dados()));
        } else if (msg.tipo().equals("enigma")) {
            mc.setScreen(new TelaDiarioEnigma(msg.dados()));
        } else if (msg.tipo().equals("desencanto")) {
            if (mc.screen instanceof TelaDesencanto t) t.atualizar(msg.dados());   // a bancada abre como menu; aqui só chega a resposta
        } else if (msg.tipo().equals("contrabando")) {
            if (mc.screen instanceof TelaContrabando t) t.atualizar(msg.dados()); else mc.setScreen(new TelaContrabando(msg.dados()));
        } else if (msg.tipo().equals("dragoes")) {
            if (mc.screen instanceof TelaDragoes t) t.atualizar(msg.dados()); else mc.setScreen(new TelaDragoes(msg.dados()));
        } else if (msg.tipo().equals("codigosite")) {
            if (mc.screen instanceof TelaCodigoSite t) t.atualizar(msg.dados()); else mc.setScreen(new TelaCodigoSite(msg.dados()));
        } else if (msg.tipo().equals("bolsosdados")) {
            BolsosCliente.receber(msg.dados());
        } else if (msg.tipo().equals("costureira")) {
            if (mc.screen instanceof TelaCostura t) t.atualizar(msg.dados());   // a mesa abre como menu; aqui só chega a resposta
        } else if (msg.tipo().equals("contratos")) {
            if (mc.screen instanceof TelaContratos t) t.atualizar(msg.dados()); else mc.setScreen(new TelaContratos(msg.dados()));
        } else if (msg.tipo().equals("terrenos")) {
            if (mc.screen instanceof TelaTerrenos t) t.atualizar(msg.dados()); else mc.setScreen(new TelaTerrenos(msg.dados()));
        } else if (msg.tipo().equals("forja")) {
            if (mc.screen instanceof TelaForja t) t.atualizar(msg.dados()); else mc.setScreen(new TelaForja(msg.dados()));
        }
    }

    /** Caixa com borda (cartão). */
    public static void cartao(GuiGraphics g, int x, int y, int l, int a) {
        g.fill(x, y, x + l, y + a, PERGAMINHO_ESC);
        g.fill(x + 1, y + 1, x + l - 1, y + a - 1, CAIXA);
    }

    /** Desenha um valor em cobre como "3 [ouro] 2 [prata] 5 [cobre]" com os ícones das moedas. Retorna a largura. */
    public static int moedas(GuiGraphics g, Font font, int x, int y, long valor, int cor) {
        long[] q = {valor / 100, (valor % 100) / 10, valor % 10};
        ItemStack[] icones = {new ItemStack(Moedas.OURO.get()), new ItemStack(Moedas.PRATA.get()), new ItemStack(Moedas.COBRE.get())};
        int x0 = x;
        boolean algum = false;
        for (int i = 0; i < 3; i++) {
            if (q[i] <= 0 && !(i == 2 && !algum)) continue;
            algum = true;
            String t = String.valueOf(q[i]);
            g.drawString(font, t, x, y, cor, false);
            x += font.width(t) + 1;
            g.pose().pushPose();
            g.pose().translate(x, y - 2, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icones[i], 0, 0);
            g.pose().popPose();
            x += 14;
        }
        return x - x0;
    }
}
