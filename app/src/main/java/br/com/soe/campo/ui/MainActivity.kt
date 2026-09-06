package br.com.soe.campo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.soe.campo.ui.navigation.SoeNavHost
import br.com.soe.campo.ui.theme.SoeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
