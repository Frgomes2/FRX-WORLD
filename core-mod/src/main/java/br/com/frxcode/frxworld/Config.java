package br.com.frxcode.frxworld;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuração em config/frxworld-common.toml. */
public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    // ------------------------------------------------------------------ painel
    public static final ForgeConfigSpec.ConfigValue<String> PAINEL_URL = B
            .comment("Endereço do painel (sem barra no final).")
            .define("painel.url", "http://localhost:3000");

    public static final ForgeConfigSpec.ConfigValue<String> SERVIDOR_TOKEN = B
            .comment("Mesmo valor de SERVIDOR_TOKEN no .env do painel.")
            .define("painel.token", "troque-por-um-token-longo");

    // ------------------------------------------------------------------ acesso
    public static final ForgeConfigSpec.BooleanValue ACESSO_ATIVO = B
            .comment("Exigir convite no primeiro login. Desligue só para testes locais.")
            .define("acesso.exigirConvite", true);

    public static final ForgeConfigSpec.IntValue SEGUNDOS_PARA_CONVITE = B
            .comment("Tempo que o jogador novo tem para digitar /convite antes de ser desconectado.")
            .defineInRange("acesso.segundosParaConvite", 180, 30, 1800);

    // ------------------------------------------------------------------ progressão
    public static final ForgeConfigSpec.IntValue NIVEL_MAXIMO = B
            .comment("Nível máximo do jogador.")
            .defineInRange("progressao.nivelMaximo", 100, 10, 1000);

    public static final ForgeConfigSpec.DoubleValue XP_BASE = B
            .comment("XP para ir do nível 1 ao 2. Fórmula: base * nivel ^ expoente.")
            .defineInRange("progressao.xpBase", 100.0, 1.0, 100000.0);

    public static final ForgeConfigSpec.DoubleValue XP_EXPOENTE = B
            .comment("Quanto a curva acelera nos níveis altos (1.0 = linear).")
            .defineInRange("progressao.xpExpoente", 1.5, 1.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLICADOR = B
            .comment("Multiplicador geral de XP ganho (eventos de XP em dobro = 2.0).")
            .defineInRange("progressao.xpMultiplicador", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {}
}
