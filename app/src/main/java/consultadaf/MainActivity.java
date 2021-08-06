package consultadaf;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dafpaf.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;

import pdafusb.APIFiscal;
import pdafusb.ARQ;
import pdafusb.Enquadramento;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity {

    /**
     * Notificações USB serão recebidas aqui.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    String nome = usbService.getIdProduct();
                    if (nome.equals("DAF-SC")){
                        if (dialogConexaoUSB != null  && dialogConexaoUSB.isShowing()){
                            fecharDialogoAguardandoConexaoUSB();
                            abrirDialogoAguardandoAppDaf(true);
                        }else if (dialogAppDAF == null){
                            abrirDialogoAguardandoAppDaf(false);
                        }
                    }
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    if (dialogConexaoUSB == null){
                        abrirDialogoAguardandoConexaoUSB();
                    }
                    else if(!dialogConexaoUSB.isShowing()){
                        abrirDialogoAguardandoConexaoUSB();
                    }
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    break;
            }
        }
    };

    // Comunicação Serial
    private static UsbService usbService;

    // Spinner para comandos da depuração
    private Spinner comandos;

    // Handler timeout
    private Handler handlerTimeout;
    private Runnable timeoutMain;

    // Tempos do PDAF-USB
    private final int valorTimeoutEnq = 500;
    private final int valorTimeoutArq = 2000;
    private final int valorTimeoutApp = 2500;

    // Camadas PDAF-USB
    private static Enquadramento e;
    private static APIFiscal fiscal;

    // Layout da aplicação
    private LinearLayout linearLayout;
    private TextView textoDaf;
    private TextView textoMop;
    private TextView textoVsb;
    private TextView textoHsb;
    private TextView textoFab;
    private TextView textoMdl;
    private TextView textoCnt;
    private TextView textoCrt;
    private TextView textoEst;
    private TextView textoMxd;
    private TextView textoNdf;
    private TextView textoRts;
    private TextView display;
    private TextView displayACKRx;
    private TextView displayACKTx;
    private TextView editText;
    private boolean conectouDaf;
    public static MyHandler mHandler;
    private AlertDialog dialogConexaoUSB;
    private AlertDialog dialogAppDAF;
    private static int tela;
    private static String autRetida;
    public Button exibirXML;
    private static final PafVirtual paf = new PafVirtual();
    private static Animation myFadeInAnimation;

    /**
     * Quando o botão "voltar" é pressionado retorna para o menu ou encerra a aplicação.
     */
    @Override
    public void onBackPressed()
    {
        switch (getTela()){
            // Tela de menu
            case 1:
                if(dialogConexaoUSB != null && dialogConexaoUSB.isShowing()){
                    fecharDialogoAguardandoConexaoUSB();
                }
                finish();
                break;
            // Consultar informações
            case 2:
                iniciaMenu();
                break;
            // Depuração
            case 3:
                iniciaMenu();
                break;
            // Lista de retidos
            case 4:
                iniciaMenu();
                break;
            // Descarregar retidos
            case 5:
                iniciaListaDescarregarRetido();
                break;
        }
    }

    /**
     * Telas do aplicativo.
     */
    public enum telas {
        menu(1),
        consultarInfo(2),
        depuracao(3),
        listaRetidos(4),
        descarregarRetidos(5);

        private final int valor;
        telas(int valorTipo){
            valor = valorTipo;
        }
        public int getValor(){
            return valor;
        }
    }

    /**
     * Abre uma caixa de diálogo informando que está aguando que um dispositivo seja conectado.
     * Apresenta um botão que encerra a aplicação.
     */
    private void abrirDialogoAguardandoConexaoUSB() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(false);
        builder.setView(R.layout.loading_dialog_usb);
        builder.setTitle("Nenhum dispositivo conectado");
        builder.setNegativeButton("Fechar", (arg0, arg1) -> {
            if(isConectouDaf()){
                fecharDialogoAguardandoConexaoUSB();
                finish();
            }else {
                Intent intent = new Intent(this, InfoApp.class);
                startActivity(intent);
            }
        });
        dialogConexaoUSB = builder.create();
        dialogConexaoUSB.show(); // para exibir o diálogo

        Button b = dialogConexaoUSB.getButton(DialogInterface.BUTTON_NEGATIVE);
        if(b != null) {
            b.setTextColor(Color.BLACK);
        }
    }

    /**
     * Encerra caixa de diálogo assim que um dispositivo é conectado.
     */
    private void fecharDialogoAguardandoConexaoUSB() {
        dialogConexaoUSB.dismiss();
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    /**
     * Abre uma caixa de diálogo informando que está aguando que o DAF esteja pronto para inciar
     * a comunicação. Apresenta um botão que encerra a aplicação.
     */
    private void abrirDialogoAguardandoAppDaf(boolean inicio) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(false);
        builder.setView(R.layout.loading_dialog_app);
        builder.setTitle("O DAF ainda não está pronto");
        builder.setNegativeButton("Fechar", (arg0, arg1) -> {
            fechaDialogoAguardandoAppDaf();
            finish();
        });
        dialogAppDAF = builder.create();
        dialogAppDAF.show(); // para exibir o diálogo

        if(inicio){
            verificaAppDafProntoInicio();
        }else {
            verificaAppDafPronto();
        }

        Button b = dialogAppDAF.getButton(DialogInterface.BUTTON_NEGATIVE);
        if(b != null) {
            b.setTextColor(Color.BLACK);
        }
    }

    /**
     * Encerra caixa de diálogo assim que o DAF estiver pronto.
     */
    private void fechaDialogoAguardandoAppDaf() {
        desativaTimeout();
        setConectouDaf(true);
        dialogAppDAF.dismiss();
    }

    /**
     * Verifica se o DAF está pronto para realizar a troca de dados.
     */
    public void verificaAppDafPronto(){
        timeoutMain = this::verificaAppDafPronto;
        defineTimeout(this.valorTimeoutApp);

        try {
            fiscal.getConsultarInformacoes();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Verifica se o DAF está pronto para realizar a troca de dados.
     */
    public void verificaAppDafProntoInicio(){
        iniciaProto();
        timeoutMain = this::verificaAppDafProntoInicio;
        defineTimeout(this.valorTimeoutApp);

        try {
            fiscal.getConsultarInformacoes();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Define um timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     * @param tempo tempo em milissegundos do timer
     */
    private void defineTimeout(int tempo){
        handlerTimeout.postDelayed(timeoutMain, tempo);
    }

    /**
     * Desativa o timeout para reenvio de um comando para ressincronização do protocolo
     * de comunicação
     */
    private void desativaTimeout(){
        handlerTimeout.removeCallbacks(timeoutMain);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new MyHandler(this);
        handlerTimeout = new android.os.Handler();
        setConectouDaf(false);
        iniciaProto();
        iniciaMenu();
    }

    /**
     * Envia uma sequência de bytes pela serial
     * @param data bytes a serem enviados pela serial
     */
    public static void escreveNaSerial(byte[] data){
        if (usbService != null) { // if UsbService was correctly binded, Send data
            usbService.write(data);
        }
    }

    public boolean isConectouDaf() {
        return conectouDaf;
    }

    public void setConectouDaf(boolean conectouDaf) {
        this.conectouDaf = conectouDaf;
    }

    /**
     * Retorna o identificador da layout atual
     * @return identificador do layout
     */
    public static int getTela(){
        return tela;
    }

    /**
     * Define o identificador da layout atual
     * @param novaTela layout atual
     */
    public static void setTela(int novaTela){
        tela = novaTela;
    }

    /**
     * Retorna o XML da autorização retida selecionada
     * @return XML da autorização retida
     */
    public static String getXML(){
        return autRetida;
    }

    /**
     * Define o XML da autorização retida selecionada
     * @param autRetidaNova XML da autorização retida
     */
    public static void setXML(String autRetidaNova){
        autRetida = autRetidaNova;
    }

    /**
     * Inicia o layout da tela de Descarregar Retidos Vazio. É exibida quando não há
     * nenhuma autorização retida no momento.
     */
    public void iniciaVazio() {
        setTela(telas.listaRetidos.getValor());
        setContentView(R.layout.activity_descarregar_retidos_vazio);
    }

    /**
     * Inicia o layout da tela de menu para a seleção das opções disponíveis.
     */
    public void iniciaMenu(){
        setTela(telas.menu.getValor());
        setContentView(R.layout.activity_menu);
        Button depuracao = findViewById(R.id.btnDepuracao);
        Button consultaInfo = findViewById(R.id.btnConsultarInformacoes);
        Button descarregarRetidos = findViewById(R.id.btnAutRetidas);

        // Listener para o botão Depuração
        depuracao.setOnClickListener(v -> iniciaDepuracao());

        // Listener para o botão Consultar Informações
        consultaInfo.setOnClickListener(v -> iniciaConsultarInfo());

        // Listener para o botão Descarregar Retidos
        descarregarRetidos.setOnClickListener(v -> iniciaListaDescarregarRetido());
    }

    /**
     * Inicia o layout da tela de Consultar Informações e envia envia o pedido de consultar
     * informações para o DAF.
     */
    public void iniciaConsultarInfo(){
        setTela(telas.consultarInfo.getValor());
        setContentView(R.layout.activity_consultar_informacoes);
        mHandler = new MyHandler(this);
        textoDaf = findViewById(R.id.textDaf);
        textoMop = findViewById(R.id.textMop);
        textoVsb = findViewById(R.id.textvsb);
        textoHsb = findViewById(R.id.texthsb);
        textoFab = findViewById(R.id.textfab);
        textoMdl = findViewById(R.id.textMdl);
        textoCnt = findViewById(R.id.textcnt);
        textoCrt = findViewById(R.id.textCrt);
        textoEst = findViewById(R.id.textEst);
        textoMxd = findViewById(R.id.textMxd);
        textoNdf = findViewById(R.id.textNdf);
        textoRts = findViewById(R.id.textRts);

        // Define timeout para reenvio do comando para ressincronização do protocolo
        timeoutMain = this::iniciaConsultarInfo;
        defineTimeout(this.valorTimeoutApp);

        // Envia um pedido de consultarInformacoes pela serial
        try {
            fiscal.getConsultarInformacoes();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Inicia o layout da tela com a listas de Autorizações Retidas e envia um pedido de Consultar
     * Informações para o DAF a fim de identificar essas autorizações.
     */
    public void iniciaListaDescarregarRetido(){
        setTela(telas.listaRetidos.getValor());
        setContentView(R.layout.activity_lista_retidos);
        mHandler = new MyHandler(this);
        linearLayout = findViewById(R.id.idLiRLista);

        // Define timeout para reenvio do comando para ressincronização do protocolo
        timeoutMain = this::iniciaListaDescarregarRetido;
        defineTimeout(this.valorTimeoutApp);

        // Envia um pedido de consultarInformacoes pela serial
        try {
            fiscal.getConsultarInformacoes();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Inicia o layout da tela de descarregar Autorizações Retidas e envia o pedido para
     * descarrregar a autorização selecionada.
     * @param rts autorização selecionada
     */
    public void iniciaDescarregarRetido(String rts){
        setTela(telas.descarregarRetidos.getValor());
        setContentView(R.layout.activity_descarregar_retidos);
        mHandler = new MyHandler(this);
        linearLayout = findViewById(R.id.idLiR);

        // Envia um pedido de descarregarRetido pela serial
        try {
            fiscal.getDescarregarAut(rts);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Inicia o layout da tela que exibe o XML da Autorização Retida selecionada.
     * @param fdf XML da autorização retida selecionada
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void iniciaDescarregarRetidoXml(String fdf){
        setTela(telas.descarregarRetidos.getValor());
        setContentView(R.layout.activity_descarregar_retidos);
        mHandler = new MyHandler(this);
        linearLayout = findViewById(R.id.idLiR);

        // Decodifica e formata o fragmento XML
        byte[] decodedBytes = Base64.getUrlDecoder().decode(fdf);
        fdf = new String(decodedBytes);
        fdf = Ferramentas.formataXML(fdf, 2);

        mHandler.geraTextView("Fragmento Essencial", fdf);
    }

    /**
     * Incia o protocolo de comunicação USB do DAF (PDAF-USB).
     */
    public void iniciaProto(){
        e = new Enquadramento(valorTimeoutEnq);
        ARQ a = new ARQ(3, valorTimeoutArq);
        fiscal = new APIFiscal();

        fiscal.setInferior(a);
        a.setSuperior(fiscal);
        a.setInferior(e);
        e.setSuperior(a);
    }

    /**
     * Inicia o layout da tela de depuração para o envio de dados via USB.
     */
    public void iniciaDepuracao(){
        setTela(telas.depuracao.getValor());
        setContentView(R.layout.activity_depuracao);

        myFadeInAnimation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.alpha);

        mHandler = new MyHandler(this);

        display = findViewById(R.id.textResposta);
        displayACKRx = findViewById(R.id.textRespostaAck2);
        displayACKTx = findViewById(R.id.textRespostaAck);
        editText = findViewById(R.id.textEntrada);
        comandos = findViewById(R.id.spinnerComandos);

        display.setMovementMethod(new ScrollingMovementMethod());
        editText.setMovementMethod(new ScrollingMovementMethod());
        displayACKRx.setMovementMethod(new ScrollingMovementMethod());
        displayACKTx.setMovementMethod(new ScrollingMovementMethod());

        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.comandos, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        comandos.setAdapter(adapter);

        Button confirmar = findViewById(R.id.botaoExecutar);

        // Verifica se algum comando foi selecionado no Spinner
        AdapterView.OnItemSelectedListener escolha = new AdapterView.OnItemSelectedListener(){

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item_comando = comandos.getSelectedItem().toString();
                display.setText("");
                editText.setText("");
                displayACKRx.setText("");
                displayACKTx.setText("");
                blinkOff(4);

                if (item_comando.equals("Consultar Informações")){
                    String data = paf.getConsultarInformacoes();
                    data = Ferramentas.formataString(data);
                    editText.setText(data);
                }
                // Outros comandos podem ser adicionados aqui
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        // Atribui o Spinner ao Listener
        comandos.setOnItemSelectedListener(escolha);

        // Verifica se o botão Executar foi pressionado
        confirmar.setOnClickListener(v -> {
            String item_comando = comandos.getSelectedItem().toString();
            display.setText("");
            displayACKRx.setText("");
            displayACKTx.setText("");

            if (item_comando.equals("Consultar Informações")){
                if (!editText.getText().toString().equals("")) {
                    if (usbService != null) { // if UsbService was correctly binded, Send data
                        try {
                            fiscal.getConsultarInformacoes();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                }
            }
            // Outros comandos podem ser adicionados aqui
            blink();
        });
    }

    /**
     * Adiciona uma animação fade nos itens da tela de depuração enquanto uma resposta não é obtida.
     */
    private void blink(){
        displayACKRx.startAnimation(myFadeInAnimation);
        displayACKTx.startAnimation(myFadeInAnimation);
        display.startAnimation(myFadeInAnimation);
    }

    /**
     * Desativa a animação fade da tela de depuração de um item específico.
     * @param item item a ter a animação fade desativada
     */
    private void blinkOff(int item){
        switch (item){
            case 1:
                displayACKRx.clearAnimation();
                break;
            case 2:
                display.clearAnimation();
                break;
            case 3:
                displayACKTx.clearAnimation();
                break;
            case 4:
                displayACKRx.clearAnimation();
                display.clearAnimation();
                displayACKTx.clearAnimation();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Começa a ouvir as notificações do UsbService
        iniciaServico(usbConnection, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);

        // Verifica se o serviço USB foi iniciado anteriormente
        if (UsbService.SERVICE_CONNECTED) {
            encerraServico();
        }
    }

    /**
     * Inicia o serviço de conexão USB
     * @param serviceConnection serviço de conexão
     * @param extras informações extras
     */
    private void iniciaServico(ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, UsbService.class);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, UsbService.class);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Encerra o serviço de conexão USB
     */
    private void encerraServico() {
        if (UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, UsbService.class);
            stopService(startService);
        }
    }

    /**
     * Realiza o registo do Receiver
     */
    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    View.OnClickListener btnListaDfe = v -> {
        Button b = (Button)v;
        String buttonText = b.getText().toString();
        buttonText = "\"" + buttonText +  "\"";
        iniciaDescarregarRetido(buttonText);
    };

    View.OnClickListener btnClickedXml = v -> iniciaDescarregarRetidoXml(getXML());

    /**
     * This handler will be passed to UsbService. Data received from serial port is displayed
     * through this handler
     */
    public static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        private boolean vazio = false;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UsbService.MESSAGE_FROM_SERIAL_PORT) {
                byte[] data = (byte[]) msg.obj;
                try {
                    e.notifica(data, (byte) -1);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }

        /**
         * Atualiza os valores dos TextViews do layout atual.
         * @param data resposta de um comando
         */
        public void handlerTelas(String data){
            switch (MainActivity.getTela()){
                case 1:
                    mActivity.get().fechaDialogoAguardandoAppDaf();
                    break;
                case 2:
                    atualizaConsultar(data);
                    break;
                case 3:
                    atualizaDisplay(data);
                    break;
                case 4:
                    atualizaListaAutRetidas(data);
                    break;
                case 5:
                    if(!isVazio())
                        atualizaAutRetidas(data);
                    break;
            }
        }

        /**
         * Adiciona o valor ao TextView do ACK enviado da tela de depuração.
         * @param data valor do ACK
         */
        public void atualizaDisplayAck(String data) {
            mActivity.get().displayACKTx.append(data);
            mActivity.get().blinkOff(1);
        }

        /**
         * Adiciona o valor ao TextView do ACK recebido da tela de depuração.
         * @param data valor do ACK
         */
        public void atualizaDisplayAckAux(String data) {
            mActivity.get().displayACKRx.append(data);
            mActivity.get().blinkOff(3);
        }

        /**
         * Adiciona o valor ao TextView da resposta recebida referente ao pedido feito na tela
         * de depuração.
         * @param data resposta recebida
         */
        public void atualizaDisplay(String data) {
            mActivity.get().display.append(Ferramentas.formataString(data));
            mActivity.get().blinkOff(2);
        }

        /**
         * Atualiza os valores dos TextViews da tela de Consultar Informações
         * @param dados resposta do pedido Consultar Informações
         */
        public void atualizaConsultar(String dados){
            try {
                JSONObject jsonObject = new JSONObject(dados);
                if(jsonObject.has("daf") && jsonObject.has("mop")){
                    String daf = jsonObject.get("daf").toString();
                    String mop = jsonObject.get("mop").toString();
                    String vsb = jsonObject.get("vsb").toString();
                    String hsb = jsonObject.get("hsb").toString();
                    String fab = jsonObject.get("fab").toString();
                    String mdl = jsonObject.get("mdl").toString();
                    String cnt = jsonObject.get("cnt").toString();
                    String crt = jsonObject.get("crt").toString();
                    String est = jsonObject.get("est").toString();
                    String mxd = jsonObject.get("mxd").toString();
                    String ndf = jsonObject.get("ndf").toString();
                    String rts = jsonObject.get("rts").toString();

                    rts = Ferramentas.formataItens(rts);
                    mActivity.get().textoDaf.append(daf);
                    mActivity.get().textoMop.append(mop);
                    mActivity.get().textoVsb.append(vsb);
                    mActivity.get().textoHsb.append(hsb);
                    mActivity.get().textoFab.append(fab);
                    mActivity.get().textoMdl.append(mdl);
                    mActivity.get().textoCnt.append(cnt);
                    mActivity.get().textoCrt.append(crt);
                    mActivity.get().textoEst.append(est);
                    mActivity.get().textoMxd.append(mxd);
                    mActivity.get().textoNdf.append(ndf);
                    mActivity.get().textoRts.append(rts);

                    // Desativa o timeout para reenvio da mensagem Consultar Informações
                    mActivity.get().desativaTimeout();
                }else{
                    mActivity.get().textoDaf.append("Erro");
                }
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }

        }

        /**
         * Verifica se há ou não autorizações retidas
         * @return verdadeiro se não há autorizações retidas e falso se houver
         */
        public boolean isVazio() {
            return vazio;
        }

        /**
         * Define se há ou não autorizações retidas
         * @param vazio verdadeiro se não há autorizações retidas e falso se houver
         */
        public void setVazio(boolean vazio) {
            this.vazio = vazio;
        }

        /**
         * Gera a lista de autorizações retidas contidas no DAF.
         * @param dados resposta do pedido Consultar Informações
         */
        @SuppressLint("ResourceType")
        public void atualizaListaAutRetidas(String dados){
            try {
                JSONObject jsonObject = new JSONObject(dados);

                // Verifica se possui o campo rts
                if(jsonObject.has("rts")){
                    ArrayList<String> listdata = new ArrayList<>();
                    try {
                        JSONArray jArray = (JSONArray) jsonObject.get("rts");

                        // Converte o conteúdo para um JSONArray
                        for (int i=0;i<jArray.length();i++){
                            try {
                                listdata.add(jArray.getString(i));
                            } catch (JSONException jsonException) {
                                jsonException.printStackTrace();
                            }
                        }
                    } catch (JSONException jsonException) {
                        jsonException.printStackTrace();
                    }

                    String rts = null;
                    try {
                        rts = jsonObject.get("rts").toString();
                    } catch (JSONException jsonException) {
                        jsonException.printStackTrace();
                    }
                    assert rts != null;
                    rts = rts.replace("[", "");
                    rts = rts.replace("]", "");
                    if(rts.equals("") || rts.equals(" ")){
                        setVazio(true);
                        mActivity.get().iniciaVazio();
                    }else{
                        setVazio(false);
                        Button[] btnWord = new Button[listdata.size()];
                        Resources r = mActivity.get().getResources();
                        int px15 = Ferramentas.intToPx(r, 15);
                        int px1 = Ferramentas.intToPx(r, 1);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0,px15,0,0);

                        // Gera a lista de autorizações retidas
                        int i = 0;
                        for(String c : listdata) {
                            btnWord[i] = new Button(mActivity.get());
                            btnWord[i].setText(c);
                            btnWord[i].setTextSize(14);
                            btnWord[i].setTextColor(0xFF000000);
                            btnWord[i].setAllCaps(false);
                            btnWord[i].setTag(i);
                            btnWord[i].setOnClickListener(mActivity.get().btnListaDfe);
                            btnWord[i].setBackgroundColor(Color.TRANSPARENT);
                            btnWord[i].setTypeface(Typeface.create("monospace", Typeface.NORMAL));
                            btnWord[i].setLayoutParams(params);
                            btnWord[i].setGravity(Gravity.START);
                            mActivity.get().linearLayout.addView(btnWord[i]);
                            i++;

                            View vi = new View(mActivity.get());
                            vi.setBackgroundColor(0xFFAAAAAA);
                            vi.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px1));
                            mActivity.get().linearLayout.addView(vi);
                        }
                        // Desativa o timeout para reenvio da mensagem Consultar Informações
                        mActivity.get().desativaTimeout();
                    }
                }

            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
        }

        /**
         * Gera os TextViews da tela de Descarregar Retido
         * @param dados respos do pedido de Descarregar Retido
         */
        @SuppressLint("ResourceType")
        @RequiresApi(api = Build.VERSION_CODES.O)
        public void atualizaAutRetidas(String dados){
            if(!isVazio()) {
                try {
                    JSONObject jsonObject = new JSONObject(dados);
                    if(jsonObject.has("jwt")){
                        String jwt = jsonObject.get("jwt").toString();
                        jwt = jwt.replace("\"", "");
                        jwt = Ferramentas.decodeJWT(jwt);
                        JSONObject jsonObjectJWT = new JSONObject(jwt);

                        if(jsonObjectJWT.has("daf")){
                            String jwtDaf = jsonObjectJWT.get("daf").toString();
                            String jwtVsb = jsonObjectJWT.get("vsb").toString();
                            String jwtMop = jsonObjectJWT.get("mop").toString();
                            String jwtPdv = jsonObjectJWT.get("pdv").toString();
                            String jwtCnt = jsonObjectJWT.get("cnt").toString();
                            String jwtAut = jsonObjectJWT.get("aut").toString();

                            geraTextView("Identificador único do DAF", jwtDaf);
                            geraTextView("Versão atual do software básico", jwtVsb);
                            geraTextView("Modo de operação do DAF", jwtMop);
                            geraTextView("Identificador único do PDV", jwtPdv);
                            geraTextView("Valor do contador monotônico", jwtCnt);
                            geraTextView("Identificador único da autorização DAF", jwtAut);
                        }

                        if (jsonObject.has("hdf")) {
                            String hdf = jsonObject.get("hdf").toString();
                            geraTextView("Resumo criptográfico do DF-e completo", hdf);
                        }
                        if (jsonObject.has("fdf")) {
                            String fdf = jsonObject.get("fdf").toString();

                            // Botão para a tela com o Fragmento XML Essencial
                            Resources r = mActivity.get().getResources();
                            int px15 = Ferramentas.intToPx(r,15);
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            params.setMargins(0,px15,0,0);
                            setXML(fdf);
                            mActivity.get().exibirXML = new Button(mActivity.get());
                            mActivity.get().exibirXML.setText("Fragmento Essencial");
                            mActivity.get().exibirXML.setTextSize(14);
                            mActivity.get().exibirXML.setTextColor(0xFF000000);
                            mActivity.get().exibirXML.setBackground(mActivity.get().getResources().getDrawable(R.drawable.bordas_botao));
                            mActivity.get().exibirXML.setLayoutParams(params);
                            mActivity.get().exibirXML.setOnClickListener(mActivity.get().btnClickedXml);
                            mActivity.get().linearLayout.addView(mActivity.get().exibirXML);
                       }
                    }
                } catch (JSONException jsonException) {
                    jsonException.printStackTrace();
                }
            }
        }

        /**
         * Gera o layout padrão de exibição de dados do aplicativo: um TextView com o título, um
         * TextView com o conteúdo e um View como divisor.
         * @param titulo título do campo
         * @param texto conteúdo do campo
         */
        @SuppressLint("ResourceType")
        public void geraTextView(String titulo, String texto){
            Resources r = mActivity.get().getResources();
            int px1 = Ferramentas.intToPx(r, 1);
            int px5 = Ferramentas.intToPx(r, 5);
            int px10 = Ferramentas.intToPx(r, 10);
            int px15 = Ferramentas.intToPx(r, 15);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0,px15,0,0);
            TextView valueTVTitulo = new TextView(mActivity.get());
            valueTVTitulo.setText(titulo);
            valueTVTitulo.setId(1);
            valueTVTitulo.setTextSize(14);
            valueTVTitulo.setLayoutParams(params);
            valueTVTitulo.setTextColor(0xFFAAAAAA);
            valueTVTitulo.setPadding(px10, 0, 0, 0);

            mActivity.get().linearLayout.addView(valueTVTitulo);

            TextView valueTV = new TextView(mActivity.get());
            valueTV.setText(texto);
            valueTV.setId(1);
            valueTV.setTextSize(14);
            valueTV.setTextColor(0xFF000000);
            valueTV.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            valueTV.setPadding(px10, px5, px10, px5);

            mActivity.get().linearLayout.addView(valueTV);

            View vi = new View(mActivity.get());
            vi.setBackgroundColor(0xFFAAAAAA);
            vi.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px1));

            mActivity.get().linearLayout.addView(vi);
        }
    }
}