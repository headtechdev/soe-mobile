package br.com.soe.campo.data

/**
 * Itens do checklist do veiculo, conforme a secao 4.3 (pre-operacional) e a
 * 4.8 (encerramento) do modulo Logistica & Mobilidade.
 *
 * Ficam no app, e nao no servidor, por um motivo operacional: o motorista
 * precisa conseguir rodar o pre-operacional no primeiro uso do aparelho,
 * antes do primeiro pull e, muitas vezes, sem rede nenhuma na garagem.
 *
 * `critical` marca a nao conformidade que bloqueia a saida. O bloqueio pode
 * ser liberado por excecao autorizada, que exige justificativa e fica
 * registrada — nunca some silenciosamente.
 */
object VehicleCheckCatalog {

    const val KIND_PRE_OP = "PRE_OP"
    const val KIND_CLOSE_OUT = "CLOSE_OUT"

    const val CONFORM = "CONFORM"
    const val NON_CONFORM = "NON_CONFORM"
    const val NOT_APPLICABLE = "NOT_APPLICABLE"

    data class Item(
        val key: String,
        val label: String,
        val help: String? = null,
        /** Nao conformidade aqui bloqueia a partida. */
        val critical: Boolean = false,
        /** Item que nao se aplica a todo veiculo (embarcacao, motocicleta). */
        val allowsNotApplicable: Boolean = true,
        /** Nao conformidade aqui pede foto da avaria. */
        val wantsPhoto: Boolean = false,
    )

    val preOperational = listOf(
        Item("general", "Condicao geral", "Estado do veiculo para entrar em operacao."),
        Item("cleaning", "Limpeza interna e externa"),
        Item(
            "bodywork",
            "Funilaria, pintura e avarias",
            "Registre avaria com foto e descricao.",
            wantsPhoto = true,
        ),
        Item("tires", "Pneus e estepe", critical = true),
        Item("lights", "Farois, lanternas e cintos", critical = true),
        Item(
            "brakes",
            "Freios",
            "Verificacao conforme procedimento. Nao substitui inspecao tecnica.",
            critical = true,
            allowsNotApplicable = false,
        ),
        Item("air", "Ar-condicionado"),
        Item("documents", "Documentos e equipamentos obrigatorios", critical = true),
    )

    val closeOut = listOf(
        Item("general", "Condicao geral na devolucao"),
        Item("damage", "Avarias ocorridas no turno", wantsPhoto = true),
        Item("cleaning", "Limpeza do veiculo"),
        Item("items", "Itens esquecidos por passageiros"),
        Item("documents", "Documentos e equipamentos devolvidos"),
    )

    fun items(kind: String): List<Item> =
        if (kind == KIND_CLOSE_OUT) closeOut else preOperational

    fun item(kind: String, key: String): Item? = items(kind).firstOrNull { it.key == key }

    fun title(kind: String): String =
        if (kind == KIND_CLOSE_OUT) "Checklist de encerramento" else "Checklist pre-operacional"
}
