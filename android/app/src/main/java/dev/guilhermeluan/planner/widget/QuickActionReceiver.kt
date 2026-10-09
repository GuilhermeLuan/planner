package dev.guilhermeluan.planner.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.notifications.runAsync
import kotlinx.coroutines.CancellationException

/**
 * Botões Água e Remédio do widget: registram a ação sem abrir o app. Quando não há o que fazer (sem Planner, ou
 * sem Dose pendente hoje), abre o app como melhor esforço.
 */
class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { QuickKind.valueOf(intent.getStringExtra(QuickActionsWidget.EXTRA_KIND).orEmpty()) }.getOrNull()
            ?: return
        val app = context.applicationContext as PlannerApplication
        runAsync {
            val result = try {
                Result.success(app.runQuickAction(kind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val confirmation = result.getOrNull()
            QuickActionsWidget.refresh(app, confirmation)
            if (result.isSuccess && confirmation == null) openApp(app, kind)
        }
    }

    private fun openApp(context: Context, kind: QuickKind) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (kind == QuickKind.Medicine) intent.putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_MEDICINES)
        // Pode ser barrado por restrição de segundo plano; o widget se corrige no próximo refresh.
        runCatching { context.startActivity(intent) }
    }
}
