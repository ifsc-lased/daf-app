package consultadaf;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dafpaf.R;

/**
 * Exibe informações sobre o uso do aplicativo.
 */
public class InfoApp extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info_conexao);
        Button voltar = findViewById(R.id.btnVoltar);

        voltar.setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
        finish();
    }
}
