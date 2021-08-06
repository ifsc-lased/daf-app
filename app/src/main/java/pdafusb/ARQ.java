package pdafusb;

import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import consultadaf.MainActivity;

public class ARQ extends Camada {

    private byte seqRx;
    private byte seqTx;
    private byte tipo;
    private final int limiteTentativas;
    private int tentativas;
    private int estado;
    private byte[] mensagem;
    private byte[] campoDados;
    private byte campoTipo;
    private final int timeout;
    private Handler handlerTimeout;
    private Runnable timeoutRunnable;

    /**
     * Estados da máquina de estados do ARQ
     */
    public enum estados {
        idle(0x01),
        wait(0x02);

        private final int valor;
        estados(int valorTipo){
            valor = valorTipo;
        }
        public int getValor(){
            return valor;
        }
    }

    /**
     * Tipos de cabeçalhos de Controle, onde os 4 primeiros bits representam o tipo e os 4
     * últimos a sequência.
     */
    public enum controle {
        data_0((byte)0x00), // 0000 0000
        data_1((byte)0x08), // 0000 1000
        ack_0((byte)0x80),  // 1000 0000
        ack_1((byte)0x88);  // 1000 1000

        private final byte valor;
        controle(byte valorTipo){
            valor = valorTipo;
        }
        public byte getValor(){
            return valor;
        }
    }

    /**
     * Classe responsável pela garantia de entrega do protocolo de comunicação USB do DAF (PDAF-USB)
     * @param maxTentativas número máximo de tentativas de retransmissão
     * @param timeout intervalo de tempo máximo entre recepções de dados
     */
    public ARQ(int maxTentativas, int timeout){
        this.seqRx = 0;
        this.seqTx = 0;
        this.tipo = 0;
        this.limiteTentativas = maxTentativas;
        this.tentativas = 0;
        this.timeout = timeout;
        this.estado = estados.idle.getValor();
        this.handlerTimeout = new android.os.Handler();
        this.timeoutRunnable = () -> handleTimeout();
    }

    /**
     * Monitora o timeout e reenvia a mensagem quando necessário.
     */
    private void handleTimeout(){
        if(this.estado == estados.wait.getValor()){
            if(this.tentativas == (this.limiteTentativas-1)){
                this.tentativas = 0;
                this.desativaTimeout();
                this.seqRx = (byte)(1 - this.seqRx);
                this.estado = estados.idle.getValor();
            }
        }else{
            this.defineTimeout(this.timeout);
            this.estado = estados.wait.getValor();
            this.reenvia();
        }
    }

    /**
     * Máquina de estados que verifica as garantias de entrega do PDAF-USB
     * @param campoControle campo controle recebido
     * @param campoDados campo dados recebido
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleFsm(byte campoControle, byte[] campoDados) throws IOException {
        this.campoDados = campoDados;
        if (this.estado == estados.idle.getValor()){
            this.idle(campoControle, campoDados);
        }else{
            this.wait(campoControle, campoDados);
        }
    }

    /**
     * Estado inicial da máquina de estados da Garantia de Entrega.
     * @param campoControle campo controle recebido
     * @param campoDados campo dados recebido
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void idle(byte campoControle, byte[] campoDados) throws IOException{
        // Caso tenha recebido o ACK, envia uma nova mensagem
        if(campoControle == -1){
            if(this.seqTx == 1){
                campoControle = controle.data_1.getValor();
            }else{
                campoControle = controle.data_0.getValor();
            }
            this.mensagem = this.montaQuadro(campoControle, campoDados);
            this.estado = estados.wait.getValor();
            this.defineTimeout(this.timeout);
            this.inferior.envia((byte)-1 ,this.mensagem, this.tipo);
        }else{
            // Recebe uma nova mensagem e envia um ACK
            if(((this.seqRx == 1) && (campoControle == controle.data_1.getValor())) || ((this.seqRx == 0) && (campoControle == controle.data_0.getValor()))){
                this.estado = estados.idle.getValor();
                this.ack(false);
                this.desativaTimeout();
                this.superior.notifica(this.campoDados, this.campoTipo);
            }
            // Recebe uma mensagem já recebida e reenvia um ACK
            else if(((this.seqRx == 0) && (campoControle == controle.data_1.getValor())) || ((this.seqRx == 1) && (campoControle == controle.data_0.getValor()))){
                this.estado = estados.idle.getValor();
                this.desativaTimeout();
                this.ack(true);
            }
            // Recebeu o ACK errado, reenvia a mensagem
            else{
                this.reenvia();
            }
        }
    }

    /**
     * Estado da Garantia de Entreda onde se aguarda a recepção de um ACK do pedido realizado.
     * @param campoControle campo controle recebido
     * @param campoDados campo dados recebido
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void wait(byte campoControle, byte[] campoDados) throws IOException{
        if(campoControle != -1){
            // Se recebeu o ACK correto, está apto a enviar uma nova mensagem
            if(((this.seqTx == 1) && (campoControle == controle.ack_1.getValor())) || ((this.seqTx == 0) && (campoControle == controle.ack_0.getValor()))){
                if(MainActivity.getTela() == 3) {
                    String s = Integer.toHexString((campoControle & 0xFF));
                    MainActivity.mHandler.atualizaDisplayAck(s);
                }
                this.seqTx = (byte)(1 - this.seqTx); // alterna valor entre 0 e 1
                this.estado = estados.idle.getValor();
                this.desativaTimeout();
            }

            // Recebe uma nova mensagem e envia um ACK
            if(((this.seqRx == 1) && (campoControle == controle.data_1.getValor())) || ((this.seqRx == 0) && (campoControle == controle.data_0.getValor()))){
                this.estado = estados.wait.getValor();
                this.ack(false);
                this.defineTimeout(this.timeout);
                this.superior.notifica(this.campoDados, this.campoTipo);
            }

            // Recebe uma mensagem já recebida e reenvia um ACK
            else if(((this.seqRx == 0) && (campoControle == controle.data_1.getValor())) || ((this.seqRx == 1) && (campoControle == controle.data_0.getValor()))){
                this.estado = estados.wait.getValor();
                this.defineTimeout(this.timeout);
                this.ack(true);
            }
        }
    }

    /**
     * Monta o quadro da camada de Garantia de Entrega.
     * @param campoControle campo controle a ser enviado
     * @param campoDados campo dados a ser enviado
     * @return retorna quadro ARQ
     * @throws IOException
     */
    private byte[] montaQuadro(byte campoControle, byte[] campoDados) throws IOException{
        return mensagem = concatenaBytes(campoControle, campoDados);
    }

    /**
     * Realiza a concatenação de bytes
     * @param a byte a ser concatenado
     * @param b byte a ser concatenado
     * @return bytes concatenados
     * @throws IOException
     */
    public byte[] concatenaBytes(byte a, byte[] b) throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(a);
        outputStream.write(b);
        return outputStream.toByteArray( );
    }

    /**
     * Define o cabeçalho de controle para o ACK
     * @param reenvio verdadeiro se é retransmissão, falso se não é retransmissão
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void ack(boolean reenvio) throws IOException{
        byte[] ack = new byte[1];
        byte seqEnvio = this.seqRx;

        if(reenvio){
            seqEnvio = (byte)(1 - seqEnvio);
        }

        if(seqEnvio == 1){
            ack[0] = controle.ack_1.getValor();
        }else{
            ack[0] = controle.ack_0.getValor();
        }

        if(!reenvio){
            this.seqRx = (byte)(1 - this.seqRx);
        }
        if(MainActivity.getTela() == 3) {
            String s = Integer.toHexString((ack[0] & 0xFF));
            MainActivity.mHandler.atualizaDisplayAckAux(s);
        }
        byte enviarMensagem = (byte) 0x01;
        this.inferior.envia((byte)-1, ack, enviarMensagem);
    }

    /**
     * Define um timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     * @param tempo tempo em milissegundos do timer
     */
    private void defineTimeout(int tempo){
        this.handlerTimeout.postDelayed(this.timeoutRunnable, tempo);
    }

    /**
     * Desativa o timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     */
    private void desativaTimeout(){
        this.handlerTimeout.removeCallbacks(this.timeoutRunnable);
    }

    /**
     * Realiza a retransmissão da última mensagem.
     */
    private void reenvia() {
        this.tentativas += 1;
        this.defineTimeout(this.timeout);
        try {
            this.inferior.envia((byte)-1, this.mensagem, this.tipo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Trata mensagens vindas da subcamada inferior (Enquadramento)
     * @param campoDados campo dados a ser tratado
     * @param campoTipo campo tipo a ser tratado
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void notifica(byte[] campoDados, byte campoTipo) throws IOException{
        this.campoTipo = campoTipo;
        byte campoControle = campoDados[0];
        this.handleFsm(campoControle, campoDados);
    }

    /**
     * Trata mensagens vindas da subcamada superior (API-PAF)
     * @param controle campo controle
     * @param data campo dados
     * @param tipo campo comando
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void envia(byte controle, byte[] data, byte tipo) throws IOException {
        this.tipo = tipo;
        this.handleFsm((byte)-1, data);
    }
}
