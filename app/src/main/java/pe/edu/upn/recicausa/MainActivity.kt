package pe.edu.upn.recicausa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pe.edu.upn.recicausa.ui.navigation.AppNavigation
import pe.edu.upn.recicausa.ui.theme.ReciCausaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ReciCausaApp).container
        setContent {
            ReciCausaTheme {
                AppNavigation(container)
            }
        }
    }
}
