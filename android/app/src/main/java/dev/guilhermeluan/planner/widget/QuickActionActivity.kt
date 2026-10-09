package dev.guilhermeluan.planner.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.guilhermeluan.planner.MainActivity
import dev.guilhermeluan.planner.PlannerApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trampolim invisível dos botões Água e Remédio. Um clique de widget sempre pode abrir uma Activity, mas um receiver
 * disparado por ele não pode abrir o app de forma confiável; por isso a decisão "agir ou abrir o app" fica aqui.
 * Age sem mostrar nada e fecha; abre o app só quando não há o que fazer (sem Planner, ou sem Dose pendente).
 */
class QuickActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = runCatching { QuickKind.valueOf(intent.getStringExtra(QuickActionsWidget.EXTRA_KIND).orEmpty()) }.getOrNull()
        if (kind == null) {
            finish()
            return
        }
        val app = application as PlannerApplication
        // O escopo do app, não o da Activity: a gravação termina mesmo se o sistema destruir esta tela.
        CoroutineScope(Dispatchers.IO).launch {
            // Falha ao gravar não é "nada a fazer": não abre o app como se não houvesse Dose ou Planner.
            val result = try {
                Result.success(app.runQuickAction(kind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val confirmation = result.getOrNull()
            QuickActionsWidget.refresh(app, confirmation)
            withContext(Dispatchers.Main) {
                if (result.isSuccess && confirmation == null) openApp(kind)
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun openApp(kind: QuickKind) {
        val intent = Intent(this, MainActivity::class.java)
        if (kind == QuickKind.Medicine) intent.putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_MEDICINES)
        startActivity(intent)
    }
}
