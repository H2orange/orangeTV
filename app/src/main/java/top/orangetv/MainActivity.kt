package top.orangetv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.nav.AppNav
import top.orangetv.ui.theme.OrangeTvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val vm: AppViewModel = viewModel()
            val themeId by vm.themeId.collectAsStateWithLifecycle()
            OrangeTvTheme(themeId = themeId) {
                AppNav(vm)
            }
        }
    }

    override fun onDestroy() {
        // 退出应用时关掉局域网端口，别留一个常驻监听
        if (isFinishing) {
            (application as OrangeTvApp).container.stopConfigServer()
        }
        super.onDestroy()
    }
}
