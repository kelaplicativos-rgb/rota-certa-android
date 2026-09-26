package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId

/**
 * Narrow adapter between the existing Agenda script editor and the dedicated Boost executor.
 * CREATE_TRIPS continues to use the untouched 0558 parser/planner path.
 */
internal class AgendaTripBoostScriptAdapter0562(
    private val activity: Activity,
    private val input: EditText,
    private val output: TextView,
) {
    private val accounts = BlaBlaDynamicAccountRegistry(activity)
    private val tripStore = TripStore(activity)
    private val ledger = BlaBlaPublicationBoostSyncStateStore0562(activity)

    fun handlesCurrentJson(): Boolean = BlaBlaTripBoostCommandParser0562.isBoostCommand(input.text.toString())

    fun validateOnly(): Boolean {
        if (!handlesCurrentJson()) return false
        val prepared = resolve() ?: return true
        val (command, target) = prepared
        BlaBlaTripBoostTrace0562.record(activity, "BOOST_COMMAND_VALIDATED", detail = "desired=${command.desiredState.name} target=${target.code.name}")
        output.text = buildString {
            append("SET_TRIP_BOOST válido ✅\n")
            append("Ação: ${if (command.desiredState == BlaBlaTripBoostDesiredState0562.ENABLED) "Ativar" else "Desativar"} Boost\n")
            append("tripId: confirmado\nprofileUuid: confirmado\n")
            append("Sessão cadastrada: exatamente uma ✅\n")
            if (command.explicitDate.isNotBlank()) append("Data cross-check: ${command.explicitDate} ✅\n")
            append("Nenhuma navegação mutável ou escrita foi realizada.")
        }
        return true
    }

    fun preview(): Boolean {
        if (!handlesCurrentJson()) return false
        val prepared = resolve() ?: return true
        val (command, target) = prepared
        val trip = requireNotNull(target.trip)
        val account = requireNotNull(target.account)
        val known = ledger.list().firstOrNull { it.tripId.equals(trip.blablaTripId, true) && it.profileUuid.equals(trip.blablaProfileUuid, true) && it.desiredState == command.desiredState }
        val stops = trip.stops.sortedBy(TripStop::order)
        val departure = Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault())
        output.text = buildString {
            append("PRÉ-VISUALIZAÇÃO — SET_TRIP_BOOST\n\n")
            append("Ação: ${if (command.desiredState == BlaBlaTripBoostDesiredState0562.ENABLED) "Ativar" else "Desativar"} Boost\n")
            append("Perfil: ${account.displayLabel}\nprofileUuid: confirmado (…)${requireNotNull(trip.blablaProfileUuid).takeLast(8)}\n")
            append("tripId: confirmado (…)${requireNotNull(trip.blablaTripId).takeLast(8)}\n")
            append("Viagem: ${departure.toLocalDate()} • ${departure.toLocalTime().withSecond(0).withNano(0)}\n")
            if (stops.size >= 2) append("${stops.first().name} → ${stops.last().name}\n")
            append("Estado desejado: ${command.desiredState.name}\n")
            append("Último estado externo conhecido: ${known?.lastObservedState?.name ?: "UNKNOWN"}\n")
            append("Nenhuma escrita foi realizada.")
        }
        return true
    }

    fun simulate(): Boolean {
        if (!handlesCurrentJson()) return false
        return launch(BlaBlaTripBoostActivity0562.MODE_SIMULATE, REQUEST_BOOST_SIMULATE)
    }

    fun execute(): Boolean {
        if (!handlesCurrentJson()) return false
        return launch(BlaBlaTripBoostActivity0562.MODE_EXECUTE, REQUEST_BOOST_EXECUTE)
    }

    fun reconcilePending(): Boolean {
        val pending = ledger.pending().firstOrNull() ?: return false
        output.text = "Reconciliando Boost pendente por leitura externa. Nenhuma escrita automática será feita."
        activity.startActivityForResult(
            Intent(activity, BlaBlaTripBoostActivity0562::class.java)
                .putExtra(BlaBlaTripBoostActivity0562.EXTRA_OPERATION_ID, pending.operationId)
                .putExtra(BlaBlaTripBoostActivity0562.EXTRA_MODE, BlaBlaTripBoostActivity0562.MODE_RECONCILE),
            REQUEST_BOOST_RECONCILE,
        )
        return true
    }

    fun historyPrefix(): String {
        val all = ledger.list()
        if (all.isEmpty()) return ""
        return buildString {
            append("SET_TRIP_BOOST — histórico operacional\n")
            all.take(20).forEach { op ->
                append("${op.syncState.name} • ${op.desiredState.name} • trip …${op.tripId.takeLast(8)} • tentativas ${op.attempts}\n")
            }
            append('\n')
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in setOf(REQUEST_BOOST_SIMULATE, REQUEST_BOOST_EXECUTE, REQUEST_BOOST_RECONCILE)) return false
        val message = data?.getStringExtra(BlaBlaTripBoostActivity0562.EXTRA_MESSAGE).orEmpty()
        val operationId = data?.getStringExtra(BlaBlaTripBoostActivity0562.EXTRA_OPERATION_ID).orEmpty()
        val op = ledger.get(operationId)
        output.text = buildString {
            append(if (resultCode == Activity.RESULT_OK) "Boost — operação concluída com evidência ✅\n" else "Boost — operação não confirmada ⚠️\n")
            append(message.ifBlank { "Sem mensagem adicional." }).append('\n')
            if (op != null) append("Estado operacional: ${op.syncState.name}\nÚltimo estado observado: ${op.lastObservedState.name}\n")
            if (op?.syncState == BlaBlaTripBoostSyncState0562.WRITE_AMBIGUOUS) append("Use Reconciliar pendências / retomar ativa. Nenhuma nova escrita ocorrerá durante a reconciliação.")
        }
        return true
    }

    private fun launch(mode: String, requestCode: Int): Boolean {
        val prepared = resolve() ?: return true
        val (command, target) = prepared
        val operation = ledger.prepare(command, target)
        // A previously verified idempotency key is still externally pre-read by the Activity;
        // it is never converted into a blind write or blind success.
        output.text = if (mode == BlaBlaTripBoostActivity0562.MODE_SIMULATE) {
            "SIMULAÇÃO — abrindo apenas para leitura do alvo exato. O controle Boost e Salvar não serão acionados."
        } else {
            "EXECUÇÃO — identidade será revalidada, estado será pré-lido e somente a diferença mínima poderá ser salva."
        }
        activity.startActivityForResult(
            Intent(activity, BlaBlaTripBoostActivity0562::class.java)
                .putExtra(BlaBlaTripBoostActivity0562.EXTRA_OPERATION_ID, operation.operationId)
                .putExtra(BlaBlaTripBoostActivity0562.EXTRA_MODE, mode),
            requestCode,
        )
        return true
    }

    private fun resolve(): Pair<BlaBlaTripBoostCommand0562, BlaBlaTripBoostTarget0562>? = try {
        val raw = input.text.toString()
        val command = BlaBlaTripBoostCommandParser0562.parse(raw)
        val target = BlaBlaTripBoostTargetResolver0562.resolve(command, tripStore.trips(), accounts.list(), ZoneId.systemDefault())
        if (target.code != BlaBlaTripBoostTargetCode0562.OK) {
            BlaBlaTripBoostTrace0562.record(activity, "BOOST_FAILED_BLOCKING", detail = "stage=resolve code=${target.code.name}")
            output.text = "SET_TRIP_BOOST bloqueado ❌\n${target.message}\nNenhuma escrita foi realizada."
            null
        } else command to target
    } catch (error: Throwable) {
        BlaBlaTripBoostTrace0562.record(activity, "BOOST_FAILED_BLOCKING", detail = "stage=parse reason=${error.message.orEmpty().take(120)}")
        output.text = "SET_TRIP_BOOST inválido ❌\n${error.message ?: "Erro desconhecido"}\nNenhuma escrita foi realizada."
        null
    }

    companion object {
        const val REQUEST_BOOST_SIMULATE = 5621
        const val REQUEST_BOOST_EXECUTE = 5622
        const val REQUEST_BOOST_RECONCILE = 5623
    }
}
