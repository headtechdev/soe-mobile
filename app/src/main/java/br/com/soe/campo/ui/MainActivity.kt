package br.com.soe.campo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.soe.campo.sync.FieldNotifications
import br.com.soe.campo.ui.navigation.SoeNavHost
import br.com.soe.campo.ui.theme.SoeTheme

class MainActivity : ComponentActivity() {

    /**
     * Notificacao e opcional: se o operador negar, o app segue funcionando e
     * so deixa de avisar fora da tela. Por isso nao tratamos o resultado.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FieldNotifications.ensureChannels(this)
        requestNotificationPermission()

        setContent {
            SoeTheme {
                val viewModel: SoeViewModel = viewModel(factory = SoeViewModel.Factory)
                val session by viewModel.session.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Enquanto o DataStore nao respondeu, session e null: nao
                    // decidimos a rota inicial para evitar um flash do login em
                    // quem ja esta autenticado.
                    if (session != null) {
                        SoeNavHost(
                            viewModel = viewModel,
                            startLoggedIn = session!!.isLoggedIn,
                        )
                    }
                }
            }
        }
    }

    /**
     * Ate o Android 12 a permissao e concedida na instalacao; de 13 em diante
     * precisa ser pedida, e sem ela as notificacoes de tarefa, prazo e alerta
     * critico sao descartadas em silencio pelo sistema.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
