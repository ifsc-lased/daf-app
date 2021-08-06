package pdafusb;

import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import consultadaf.MainActivity;

public class Enquadramento extends Camada {

    private int estado;
    private byte[] campoDados = {};
    private byte campoTipo;
    private final int timeout;
    private int tamanhoComando;
    private byte[] campoTamanho = {};
    private int valorTamanho;
    private final Handler handlerTimeout;
    private final Runnable timeoutRunnable;

    /**
     * Estados da máquina de estados do Enquadramento
     */
    public enum estados {
        idle(0x01),
        rxTam(0x02),
        rxDado(0x03);

        private final int valor;
        estados(int valorTipo){
            valor = valorTipo;
        }
        public int getValor(){
            return valor;
        }
    }

    /**
     * Tipos de comando que o protocolo provê suporte
     */
    public enum tipos {
        enviarMensagem((byte)0x01),
        enviarBinario((byte)0x02);

        private final byte valor;
        tipos(byte valorTipo){
            valor = valorTipo;
        }
        public byte getValor(){
            return valor;
        }
    }

    /**
     * Tipos de tamanhos que o protocolo provê suporte
     */
    public enum tamanhos {
        tam2(0x02),
        tam4(0x04);

        private final int valor;
        tamanhos(int valorTipo){
            valor = valorTipo;
        }
        public int getValor(){
            return valor;
        }
    }

    /**
     * Classe responsável pelo enquadramento do protocolo de comunicação USB do DAF (PDAF-USB)
     * @param timeout intervalo de tempo máximo entre recepções de bytes
     */
    public Enquadramento(int timeout){
        this.timeout = timeout;
        this.estado = estados.idle.getValor();
        this.handlerTimeout = new android.os.Handler();
        this.timeoutRunnable = this::handleTimeout;
    }

    /**
     * Trata os dados recebidos pela serial os enviando byte a byte para a máquina de estados
     * do protocolo.
     * @param dados sequência de bytes recebidos pela serial
     * @throws IOException
     */
    public void handle(byte[] dados) throws IOException{
        for(byte ch: dados){
            this.handleFsm(ch);
        }
    }

    /**
     * Monitora o timeout e reenvia a mensagem quando necessário.
     */
    private void handleTimeout(){
        this.zeraVariaveis();
        this.estado = estados.idle.getValor();
        this.desativaTimeout();
    }

    /**
     * Máquina de estados que trata um byte recebido.
     * @param dado byte recebido
     * @throws IOException
     */
    public void handleFsm(byte dado) throws IOException{
        if(this.estado == estados.idle.getValor()){
            this.idle(dado);
        }else if(this.estado == estados.rxTam.getValor()){
            this.rxTam(dado);
        }else{
            this.rxDado(dado);
        }
    }

    /**
     * Estado inicial que verifica o tipo do comando recebido
     * @param dado byte a ser verificado
     */
    public void idle(byte dado){
        if (dado == tipos.enviarMensagem.getValor()){
            this.campoTipo = dado;
            this.tamanhoComando = tamanhos.tam2.getValor();
            this.defineTimeout(this.timeout);
            this.estado = estados.rxTam.getValor();
        }else if(dado == tipos.enviarBinario.getValor()){
            this.campoTipo = dado;
            this.tamanhoComando = tamanhos.tam4.getValor();
            this.defineTimeout(this.timeout);
            this.estado = estados.rxTam.getValor();
        }else{
            this.estado = estados.idle.getValor();
        }
    }

    /**
     * Estado de recepção do campo Tamanho
     * @param dado byte a ser verificado
     * @throws IOException
     */
    public void rxTam(byte dado) throws IOException{
        if(this.campoTamanho.length == (this.tamanhoComando - 1)){
            this.campoTamanho = this.concatenaBytes(this.campoTamanho, dado);
            this.defineTimeout(this.timeout);
            this.valorTamanho = this.extraiTamanho(this.campoTamanho);
            this.estado = estados.rxDado.getValor();
        }else {
            this.campoTamanho = this.concatenaBytes(this.campoTamanho, dado);
            this.defineTimeout(this.timeout);
            this.estado = estados.rxTam.getValor();
        }
    }

    /**
     * Gera um byte[] vazio
     * @return byte[] vazio
     */
    public byte[] clearBytes(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        return outputStream.toByteArray( );
    }

    /**
     * Extrai o valor do campo Tamanho
     * @param tamanho campo Tamanho
     * @return valor do campo tamanho
     */
    public int extraiTamanho(byte[] tamanho){
        ByteBuffer wrapped = ByteBuffer.wrap(tamanho); // big-endian by default
        int num = 0;
        if(tamanho.length == 2){
            num = wrapped.getShort(); // 1
        }else if(tamanho.length == 4){
            num = wrapped.getInt(); // 1
        }
        return num;
    }

    /**
     * Estado de recepção do campo Dados
     * @param dado byte a ser verificado
     * @throws IOException
     */
    public void rxDado(byte dado) throws IOException{
        if(this.campoDados.length < (this.valorTamanho -1)){
            this.campoDados = concatenaBytes(campoDados, dado);
            this.defineTimeout(this.timeout);
            this.estado = estados.rxDado.getValor();
        }
        else if(this.campoDados.length == (this.valorTamanho -1)){
            this.campoDados = concatenaBytes(campoDados, dado);
            this.desativaTimeout();
            this.estado = estados.idle.getValor();
            this.superior.notifica(this.campoDados, this.campoTipo);
            this.zeraVariaveis();
            this.resetVariaveis();
        }
    }

    /**
     * Reinicia algumas variáveis do protocolo
     */
    public void zeraVariaveis(){
        this.tamanhoComando = 0;
        this.valorTamanho = 0;
    }

    /**
     * Reinicia algumas variáveis do protocolo
     */
    private void resetVariaveis() {
        this.campoTamanho = this.clearBytes();
        this.campoDados = this.clearBytes();
    }

    /**
     * Envia uma sequência de bytes pela serial.
     * @param quadro sequência de bytes que será enviada pelo barramento serial
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void escreveSerial(byte[] quadro) {
        MainActivity.escreveNaSerial(quadro);
    }

    /**
     * Cria e envia um comando do tipo enviarMensagem pela serial
     * @param dados campo dados do Dados
     * @return retorna quadro enviado
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public byte[] enviarMensagem(byte[] dados) throws IOException{
        byte[] quadro;
        quadro = montaQuadro(tipos.enviarMensagem.getValor(), tamanhos.tam2.getValor(), dados);
        this.escreveSerial(quadro);
        this.resetVariaveis();
        return quadro;
    }

    /**
     * Cria e envia um comando do tipo enviarBinario pela serial
     * @param dados campo dados do Dados
     * @return retorna quadro enviado
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public byte[] enviarBinario(byte[] dados) throws IOException{
        byte[] quadro;
        quadro = montaQuadro(tipos.enviarBinario.getValor(), tamanhos.tam4.getValor(), dados);
        this.escreveSerial(quadro);
        this.resetVariaveis();
        return quadro;
    }

    /**
     * Realiza a concatenação de bytes
     * @param a byte a ser concatenado
     * @param b byte a ser concatenado
     * @return bytes concatenados
     * @throws IOException
     */
    public byte[] concatenaBytes(byte[] a, byte b) throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(a);
        outputStream.write(b);
        return outputStream.toByteArray( );
    }

    /**
     * Realiza a concatenação de bytes
     * @param a byte a ser concatenado
     * @param b byte a ser concatenado
     * @return bytes concatenados
     * @throws IOException
     */
    public byte[] concatena2Bytes(byte[] a, byte[] b) throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(a);
        outputStream.write(b);
        return outputStream.toByteArray( );
    }

    /**
     * Realiza a concatenação de bytes
     * @param a byte a ser concatenado
     * @param b byte a ser concatenado
     * @return bytes concatenados
     */
    public byte[] concatenaSoBytes(byte a, byte b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(a);
        outputStream.write(b);
        return outputStream.toByteArray( );
    }

    /**
     * Realiza a montagem de um quadro.
     * @param tipo tipo do comando
     * @param idTamanho tamanho do campo Dados
     * @param dados conteúdo do campo Dados
     * @return retorna o quadro de um comando
     * @throws IOException
     */
    public byte[] montaQuadro(byte tipo, int idTamanho, byte[] dados) throws IOException{
        byte[] quadro = {tipo};
        int tamanhoDados = (dados.length);

        if(idTamanho == tamanhos.tam2.getValor() && tamanhoDados <= 65535){
            byte[] tamanhoBytes = ByteBuffer.allocate(4).putInt(tamanhoDados).array();
            tamanhoBytes = concatenaSoBytes(tamanhoBytes[2], tamanhoBytes[3]);
            quadro = this.concatena2Bytes(quadro, tamanhoBytes);
        }
        quadro = this.concatena2Bytes(quadro, dados);
        return quadro;
    }

    /**
     * Define um timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     * @param tempo tempo em milissegundos do timer
     */
    private void defineTimeout(int tempo){
        handlerTimeout.postDelayed(timeoutRunnable, tempo);
    }

    /**
     * Desativa o timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     */
    private void desativaTimeout(){
        handlerTimeout.removeCallbacks(timeoutRunnable);
    }

    /**
     * Trata mensagens recebidas pela serial
     * @param data campo dados a ser tratado
     * @param tipo campo tipo a ser tratado
     * @throws IOException
     */
    @Override
    public void notifica(byte[] data, byte tipo) throws IOException {
        this.handle(data);
    }

    /**
     * Trata mensagens vindas da subcamada superior (ARQ)
     * @param controle campo controle
     * @param data campo dados
     * @param tipo campo comando
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void envia(byte controle, byte[] data, byte tipo) throws IOException {
        if (tipo == tipos.enviarMensagem.getValor()){
            this.enviarMensagem(data);
        }else if(tipo == tipos.enviarBinario.getValor()){
            this.enviarBinario(data);
        }
    }
}
