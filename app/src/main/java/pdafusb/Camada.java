package pdafusb;

import java.io.IOException;

/**
 * Classe camada que deve ser implementada por todas as camadas do protocolo.
 */
public abstract class Camada {

    protected Camada superior;
    protected Camada inferior;

    /**
     * Construtor onde são definidas as camadas superiores e inferiores
     * @param superior objeto da camada superior
     * @param inferior objeto da camada inferior
     */
    public Camada(Camada superior, Camada inferior){
        this.superior = superior;
        this.inferior = inferior;
    }

    public Camada(){}

    /**
     * Mensagens que são recebidas da camada inferior
     * @param data campo Dados
     * @param tipo campo Comando
     * @throws IOException
     */
    public abstract void notifica(byte[] data, byte tipo) throws IOException;

    /**
     * Mensagens que são recebidas da camada superior
     * @param controle campo Controle
     * @param data campo Dados
     * @param tipo campo Comando
     * @throws IOException
     */
    public abstract void envia(byte controle, byte[] data, byte tipo) throws IOException;

    /**
     * Define a camada superior
     * @param superior objeto da camada superior
     */
    public void setSuperior(Camada superior){
        this.superior = superior;
    }

    /**
     * Define a camada inferior
     * @param inferior objeto da camada inferior
     */
    public void setInferior(Camada inferior){
        this.inferior = inferior;
    }
}
