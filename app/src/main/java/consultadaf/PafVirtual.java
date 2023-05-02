package consultadaf;

/***
 * Classe PafVirtual é um esboço. Possui o conteúdo de alguns comandos comandos previstos na
 * Especificação 1.0.0 do Dispositivo Autorizador Fiscal (DAF).
 */
public class PafVirtual {

    public PafVirtual() {
    }

    /**
     * Retorna String do campo Dados do comando consultarInformacoes
     * @return retorna campo Dados do comando
     */
    public String getConsultarInformacoes() {
        return "{\"msg\":8}";
    }
    public String getConsultarAutorizacoes(String ndf) {
        return "{\"msg\":16, \"ini\":1, \"fim\":"+ ndf +"}";
    }
}