package consultadaf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.example.dafpaf.R;

public class SplashScreen  extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Timer da splash screen
        int SPLASH_TIMEOUT = 1500;
        /*
         * Exibindo splash com um timer.
         */
        new Handler().postDelayed(() -> {
            // Esse método será executado sempre que o timer acabar
            // E inicia a activity principal
            Intent i = new Intent(SplashScreen.this,
                    MainActivity.class);
            startActivity(i);

            // Fecha esta activity
            finish();
        }, SPLASH_TIMEOUT);
    }
}