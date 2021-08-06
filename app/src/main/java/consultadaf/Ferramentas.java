package consultadaf;

import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.RequiresApi;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class Ferramentas {

    public Ferramentas(){}

    /***
     * Formata String de entrada para formato JSON expandido.
     * @param entrada string a ser formatada
     * @return retorna String formatada em JSON expandido.
     */
    public static String formataString(String entrada){

        StringBuilder json = new StringBuilder();
        String indentString = "";

        for (int i = 0; i < entrada.length(); i++) {
            char letter = entrada.charAt(i);
            switch (letter) {
                case '{':
                    json.append(indentString).append(letter).append("\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '[':
                    json.append("\n").append(indentString).append(letter).append("\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '}':
                case ']':
                    indentString = indentString.replaceFirst("\t", "");
                    json.append("\n").append(indentString).append(letter);
                    break;
                case ',':
                    json.append(letter).append("\n").append(indentString);
                    break;

                default:
                    json.append(letter);
                    break;
            }
        }

        return json.toString();
    }

    /**
     * Formata uma String de entrada para uma lista de itens.
     * @param entrada String a ser formatada
     * @return retorna String formatado
     */
    public static String formataItens(String entrada){
        StringBuilder json = new StringBuilder();
        json.append("• ");
        for (int i = 0; i < entrada.length(); i++) {
            char letter = entrada.charAt(i);
            switch (letter) {
                case '{':
                case '[':
                case ' ':
                case '"':
                case ']':
                    break;
                case ',':
                    json.append("\n" + "• ");
                    break;
                default:
                    json.append(letter);
                    break;
            }
        }
        return json.toString();
    }

    /**
     * Indenta uma string que contem um XML.
     * @param xml xml a ser indentado
     * @param indent tamanho da intentação
     * @return retorna o xml indentado
     */
    public static String formataXML(String xml, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(xml));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "" + indent);
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Realiza a decodificação do payload de um token JWT
     * @param jwtToken token JWT a ser decodificado
     * @return payload decodificado do JWT
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String decodeJWT(String jwtToken){
        String[] split_string = jwtToken.split("\\.");
        String base64EncodedBody = split_string[1];
        return new String(Base64.getUrlDecoder().decode(base64EncodedBody));
    }

    /***
     * Realiza a conversão de DIP para pixel
     * @param r recuros da aplicação
     * @param valor valor a ser convertido
     * @return retorna valor convertido
     */
    public static int intToPx(Resources r, int valor){
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                valor,
                r.getDisplayMetrics()
        );
    }

}
