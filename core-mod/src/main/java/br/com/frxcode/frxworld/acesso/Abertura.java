package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.habilidades.Unica;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.racas.RacasJogador;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Abertura do primeiro login: tela escura, a voz do Sistema contando que você renasceu neste mundo,
 * escolha da raça e da classe, o despertar da Habilidade Única e o nascimento na capital.
 */
public class Abertura {
    private static final String CHAVE = "frx_abertura";
    /** 0 = ainda não viu, 1 = esperando a raça, 2 = esperando a classe, 3 = concluída. */
    public static final int NADA = 0, RACA = 1, CLASSE = 2, FIM = 3, PREVIA = 4;

    public Abertura() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static int etapa(Player p) { return dados(p).getInt("etapa"); }
    private static void etapa(Player p, int e) { dados(p).putInt("etapa", e); }

    /** Está no meio da abertura (serve para a raça e a classe saberem que devem continuar a cena). */
    public static boolean emAndamento(Player p) {
        int e = etapa(p);
        return e == RACA || e == CLASSE;
    }

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("abertura"); }

    private static boolean ligada() {
        try { JsonObject o = cfg(); return o == null || !o.has("ativo") || o.get("ativo").getAsBoolean(); } catch (Exception e) { return true; }
    }

    private static String reinoInicial() {
        try { JsonObject o = cfg(); if (o != null && o.has("reino")) return o.get("reino").getAsString(); } catch (Exception ignorado) { }
        return "tempest";
    }

    /** Falas do Sistema na tela escura (editáveis no painel). */
    private static List<String> falas(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        try {
            JsonObject o = cfg();
            if (o != null && o.has("falas") && o.get("falas").isJsonArray())
                for (com.google.gson.JsonElement e : o.getAsJsonArray("falas")) l.add(e.getAsString());
        } catch (Exception ignorado) { }
        if (l.isEmpty()) l = List.of(
                "Você morreu.",
                "…mas a sua história não acabou aqui.",
                "Um mundo de oito reinos abre os olhos com você.",
                "Monstros caminham pelas estradas. Reis procuram heróis.",
                "Eu sou o Sistema. Vou acompanhar cada passo seu daqui em diante.",
                "Antes de nascer, preciso saber quem você é.");
        List<String> r = new ArrayList<>();
        for (String s : l) r.add(s.replace("{jogador}", p.getGameProfile().getName()));
        return r;
    }

    // ------------------------------------------------------------------ cena

    private static void enviarTela(ServerPlayer p, String fase, List<String> linhas, String botao) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("previa", etapa(p) == PREVIA);
        d.putString("fase", fase);
        d.putString("jogador", p.getGameProfile().getName());
        d.putString("botao", botao);
        ListTag l = new ListTag();
        for (String s : linhas) l.add(StringTag.valueOf(s));
        d.put("linhas", l);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("abertura", d));
    }

    /** Chamado quando o convite é aceito (ou ao entrar, se o jogador ainda não escolheu raça). */
    public static void iniciar(ServerPlayer p) {
        if (!ligada() || etapa(p) >= FIM) return;
        if (RacasJogador.escolheu(p) && !Progressao.dados(p).classe().isEmpty()) { etapa(p, FIM); return; }
        etapa(p, RACA);
        comecar(p);
    }

    /** Prévia para admin: mostra as telas da cena sem mexer na raça nem na classe. */
    public static void previa(ServerPlayer p) {
        etapa(p, PREVIA);
        dados(p).putInt("passo", 0);
        comecar(p);
    }

    private static void comecar(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 4, false, false));   // ninguém morre no meio da cena
        p.level().playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6f, 0.6f);
        enviarTela(p, "intro", falas(p), "Continuar");
    }

    /** Resposta do cliente: o jogador leu a introdução. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        if (etapa(p) != RACA) return;
        RacasJogador.sincronizar(p, true);   // abre a tela de raças
    }

    /** Prévia: pula a escolha e vai direto para a próxima tela da cena. */
    public static void avancarPrevia(ServerPlayer p) {
        if (etapa(p) != PREVIA) return;
        int passo = dados(p).getInt("passo") + 1;
        dados(p).putInt("passo", passo);
        if (passo == 1) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
            enviarTela(p, "classe", List.of("O corpo está escolhido.", "Agora escolha o caminho que vai trilhar."), "Continuar");
            return;
        }
        Unica.Def u = Unica.def(p);
        List<String> linhas = new ArrayList<>();
        linhas.add("Resta uma coisa que ninguém escolhe.");
        if (u != null) {
            linhas.add("Habilidade Única despertada:");
            linhas.add("« " + u.nome() + " »");
            linhas.add(u.descricao());
        }
        linhas.add("Ela é só sua, e vai crescer junto com você.");
        enviarTela(p, "unica", linhas, "Nascer neste mundo");
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.7f);
    }

    /** A raça foi escolhida: segue para a classe. */
    public static void aposRaca(ServerPlayer p) {
        if (etapa(p) != RACA) return;
        etapa(p, CLASSE);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
        enviarTela(p, "classe", List.of("O corpo está escolhido.", "Agora escolha o caminho que vai trilhar."), "Escolher classe");
    }

    /** A classe foi escolhida: desperta a Habilidade Única e nasce na capital. */
    public static void aposClasse(ServerPlayer p) {
        if (etapa(p) != CLASSE) return;
        etapa(p, FIM);
        Unica.Def u = Unica.def(p);
        List<String> linhas = new ArrayList<>();
        linhas.add("Resta uma coisa que ninguém escolhe.");
        if (u != null) {
            linhas.add("Habilidade Única despertada:");
            linhas.add("« " + u.nome() + " »");
            linhas.add(u.descricao());
        }
        linhas.add("Ela é só sua, e vai crescer junto com você.");
        enviarTela(p, "unica", linhas, "Nascer neste mundo");
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.7f);
        if (p.level() instanceof ServerLevel n)
            n.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 80, 0.5, 1.0, 0.5, 0.1);
    }

    /** Último clique: teleporta para a capital inicial e solta o jogador no mundo. */
    public static void nascer(ServerPlayer p) {
        if (etapa(p) != FIM && etapa(p) != PREVIA) return;
        etapa(p, FIM);
        Reino r = null;
        for (Reino x : Reinos.todos()) if (x.id().equals(reinoInicial())) r = x;
        if (r == null && !Reinos.todos().isEmpty()) r = Reinos.todos().get(0);
        if (r != null && p.getServer() != null) {
            ServerLevel nivel = p.getServer().overworld();
            nivel.getChunk(r.centroX() >> 4, r.centroZ() >> 4);
            int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, r.centroX(), r.centroZ());
            p.teleportTo(nivel, r.centroX() + 0.5, y + 1, r.centroZ() + 0.5, p.getYRot(), p.getXRot());
            p.setRespawnPosition(nivel.dimension(), new net.minecraft.core.BlockPos(r.centroX(), y + 1, r.centroZ()), p.getYRot(), true, false);
        }
        p.removeEffect(MobEffects.BLINDNESS);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
        String reino = r != null ? r.nome() : "este mundo";
        Sistema.avisar(p, "Bem-vindo a " + reino, "Fale com o taverneiro para pegar missões e com a recepcionista da Guilda para se registrar.", Sistema.OURO);
        p.sendSystemMessage(Component.literal("Dicas: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("tecla K = habilidades · tecla E = status · /raca · /unica · /banco").withStyle(ChatFormatting.WHITE)));
    }

    // ------------------------------------------------------------------ comando de teste

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abertura")
                        .executes(ctx -> { previa(ctx.getSource().getPlayerOrException()); return 1; })
                        .then(Commands.literal("reset").executes(ctx -> {
                            // teste de verdade: apaga raça e classe e roda a cena como um jogador novo
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RacasJogador.limpar(p);
                            br.com.frxcode.frxworld.habilidades.Habilidades.escolherClasse(p, "", true);
                            etapa(p, NADA);
                            iniciar(p);
                            return 1;
                        }))));
    }
}
