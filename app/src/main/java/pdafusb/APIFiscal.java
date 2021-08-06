package pdafusb;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import consultadaf.MainActivity;

/**
 * Classe que possui os comandos disponíveis no aplicativo.
 */
public class APIFiscal extends Camada{

    /**
     * Envia um comando consultarInformações
     * @throws IOException
     */
    public void getConsultarInformacoes() throws IOException {
        String dados = "{\"msg\":8}";
        this.envia((byte)-1, dados.getBytes(), (byte)0x01);
    }

    /**
     * Envia um comando descarregarRetido
     * @param idAut identificador único da autorização
     * @throws IOException
     */
    public void getDescarregarAut(String idAut) throws IOException {
        String dados = "{\"msg\":11,\"aut\":" + idAut+"}";
        this.envia((byte)-1, dados.getBytes(), (byte)0x01);
    }

    /**
     * Mensagens que são recebidas da camada inferior (ARQ)
     * @param data campo Dados
     * @param tipo campo Comando
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void notifica(byte[] data, byte tipo)  {
        String msgDecode = new String(data, StandardCharsets.UTF_8);
        MainActivity.mHandler.handlerTelas(msgDecode.substring(1));
    }

    /**
     * Envia mensagens para a camada inferior
     * @param controle campo Controle
     * @param data campo Dados
     * @param tipo campo Comando
     * @throws IOException
     */
    @Override
    public void envia(byte controle, byte[] data, byte tipo) throws IOException {
        this.inferior.envia((byte)-1, data, tipo);
    }
}
