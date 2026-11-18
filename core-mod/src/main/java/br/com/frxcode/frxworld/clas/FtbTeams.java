package br.com.frxcode.frxworld.clas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ponte para o FTB Teams (os clãs são os times "party" dele), por reflexão: o mod do FRX compila sem o FTB Teams
 * e, se ele faltar ou mudar, {@link #disponivel()} devolve false e o NPC dos clãs avisa em vez de quebrar.
 * Um time é tratado como Object; os métodos daqui são os únicos pontos que tocam nas classes do FTB Teams.
 */
public final class FtbTeams {
    private FtbTeams() {}

    /**
     * Acha um método público pelo nome e pelo número de parâmetros (os usados aqui não têm sobrecarga com a mesma contagem),
     * preferindo a declaração que está numa classe ou interface pública, para a chamada por reflexão ser aceita.
     */
    static Method metodo(Class<?> classe, String nome, int parametros) throws NoSuchMethodException {
        java.util.Deque<Class<?>> fila = new java.util.ArrayDeque<>(List.of(classe));
        Set<Class<?>> vistos = new java.util.HashSet<>();
        Method reserva = null;
        while (!fila.isEmpty()) {
            Class<?> c = fila.poll();
            if (c == null || !vistos.add(c)) continue;
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(nome) || m.getParameterCount() != parametros || !java.lang.reflect.Modifier.isPublic(m.getModifiers()) || m.isBridge()) continue;
                if (java.lang.reflect.Modifier.isPublic(c.getModifiers())) return m;
                if (reserva == null) reserva = m;
            }
            if (c.getSuperclass() != null) fila.add(c.getSuperclass());
            fila.addAll(List.of(c.getInterfaces()));
        }
        if (reserva != null) { try { reserva.setAccessible(true); } catch (RuntimeException e) { /* tenta assim mesmo */ } return reserva; }
        throw new NoSuchMethodException(classe.getName() + "." + nome + "/" + parametros);
    }

    private static Object chamar(Object alvo, String nome, Object... args) throws Exception {
        try {
            return metodo(alvo.getClass(), nome, args.length).invoke(alvo, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception causa) throw causa;
            throw e;
        }
    }

    private static Object gerente() throws Exception {
        Object api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
        if (!(Boolean) chamar(api, "isManagerLoaded")) throw new IllegalStateException("FTB Teams ainda não carregou");
        return chamar(api, "getManager");
    }

    public static boolean disponivel() {
        try { gerente(); return true; } catch (Throwable t) { return false; }
    }

    /** Todos os clãs (times do tipo party). */
    public static List<Object> clas() {
        List<Object> l = new ArrayList<>();
        try {
            for (Object t : (Collection<?>) chamar(gerente(), "getTeams")) if ((Boolean) chamar(t, "isPartyTeam")) l.add(t);
        } catch (Throwable t) { /* sem FTB Teams */ }
        return l;
    }

    /** O clã do jogador, ou null se ele está sozinho. */
    public static Object claDe(ServerPlayer p) {
        try {
            Object t = ((Optional<?>) chamar(gerente(), "getTeamForPlayer", p)).orElse(null);
            return t != null && (Boolean) chamar(t, "isPartyTeam") ? t : null;
        } catch (Throwable t) { return null; }
    }

    public static Object claPorId(UUID id) {
        try {
            Object t = ((Optional<?>) chamar(gerente(), "getTeamByID", id)).orElse(null);
            return t != null && (Boolean) chamar(t, "isPartyTeam") ? t : null;
        } catch (Throwable t) { return null; }
    }

    public static UUID id(Object cla) { try { return (UUID) chamar(cla, "getId"); } catch (Throwable t) { return new UUID(0, 0); } }
    public static UUID lider(Object cla) { try { return (UUID) chamar(cla, "getOwner"); } catch (Throwable t) { return new UUID(0, 0); } }
    public static String nome(Object cla) { try { return ((Component) chamar(cla, "getName")).getString(); } catch (Throwable t) { return "Clã"; } }

    @SuppressWarnings("unchecked")
    public static Set<UUID> membros(Object cla) { try { return (Set<UUID>) chamar(cla, "getMembers"); } catch (Throwable t) { return Set.of(); } }

    /** "owner", "officer", "member"... */
    public static String cargo(Object cla, UUID jogador) {
        try { Object r = chamar(cla, "getRankForPlayer", jogador); return (r instanceof Enum<?> e ? e.name() : String.valueOf(r)).toLowerCase(java.util.Locale.ROOT); } catch (Throwable t) { return "none"; }
    }

    public static boolean lideranca(Object cla, UUID jogador) { String c = cargo(cla, jogador); return c.equals("owner") || c.equals("officer"); }

    /** Dados extras do time, gravados pelo próprio FTB Teams junto com ele (o cofre e os pedidos do clã ficam aqui). */
    public static CompoundTag extra(Object cla) { try { return (CompoundTag) chamar(cla, "getExtraData"); } catch (Throwable t) { return new CompoundTag(); } }

    public static void gravar(Object cla) { try { chamar(cla, "markDirty"); } catch (Throwable t) { /* grava na próxima */ } }

    /** Nome do jogador conhecido pelo FTB Teams (serve para quem está fora do jogo). */
    public static String nomeJogador(UUID id) {
        try { return ((Component) chamar(gerente(), "getPlayerName", id)).getString(); } catch (Throwable t) { return "?"; }
    }

    /** Coloca o jogador no clã. Devolve null se deu certo, ou o motivo. */
    public static String entrar(Object cla, ServerPlayer p) {
        try { chamar(cla, "join", p); return null; } catch (Throwable t) { return motivo(t); }
    }

    public static String sair(Object cla, UUID jogador) {
        try { chamar(cla, "leave", jogador); return null; } catch (Throwable t) { return motivo(t); }
    }

    public static String expulsar(Object cla, ServerPlayer lider, com.mojang.authlib.GameProfile alvo) {
        try { chamar(cla, "kick", lider.createCommandSourceStack().withSuppressedOutput(), List.of(alvo)); return null; } catch (Throwable t) { return motivo(t); }
    }

    /** Funda um clã com o jogador como líder. Devolve o clã, ou null. */
    public static Object fundar(ServerPlayer p, String nome) {
        try { return chamar(gerente(), "createParty", p, nome); } catch (Throwable t) { return null; }
    }

    private static String motivo(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? "O FTB Teams recusou (" + t.getClass().getSimpleName() + ")." : m;
    }
}
