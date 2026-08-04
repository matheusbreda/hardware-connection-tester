import java.net.Socket;
import java.io.OutputStream;

// Cliente TCP mínimo só para testar o listener bruto.
public class TcpProbe {
    public static void main(String[] a) throws Exception {
        try (Socket s = new Socket("localhost", 9000);
             OutputStream o = s.getOutputStream()) {
            o.write("PING\r\nSENSOR:42;VALOR:99\r\n".getBytes("UTF-8"));
            o.flush();
            Thread.sleep(300);
        }
    }
}
