import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ferramenta de teste de conexão com hardwares.
 *
 * Sobe:
 *   - Um servidor HTTP catch-all: aceita QUALQUER método, rota, content-type
 *     (XML, JSON, texto, binário...) e loga tudo no console.
 *   - Um listener TCP bruto: aceita sockets puros e loga todos os bytes recebidos.
 *
 * Tudo é impresso via System.out.
 *
 * Uso (Java 21 - single file, sem compilar):
 *   java HardwareTester.java
 *   java HardwareTester.java --http 8080 --tcp 9000
 *
 * Padrões: HTTP em 8080, TCP em 9000.
 */
public class HardwareTester {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Contadores para dar um id a cada requisição/conexão nos logs.
    private static final AtomicLong HTTP_SEQ = new AtomicLong();
    private static final AtomicLong TCP_SEQ = new AtomicLong();

    public static void main(String[] args) throws Exception {
        // Força UTF-8 na saída para acentos não saírem truncados,
        // inclusive quando o log é redirecionado para arquivo no Windows.
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out),
                true, StandardCharsets.UTF_8));

        int httpPort = 8090;
        int tcpPort = 9000;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--http" -> httpPort = Integer.parseInt(args[i + 1]);
                case "--tcp"  -> tcpPort  = Integer.parseInt(args[i + 1]);
                default -> { /* ignora */ }
            }
        }

        startHttpServer(httpPort);
        startTcpServer(tcpPort);

        banner(httpPort, tcpPort);
    }

    // ------------------------------------------------------------------ HTTP

    private static void startHttpServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // Contexto "/" = catch-all: pega qualquer rota.
        server.createContext("/", HardwareTester::handleHttp);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private static void handleHttp(HttpExchange ex) {
        long id = HTTP_SEQ.incrementAndGet();
        StringBuilder sb = new StringBuilder();
        try {
            byte[] body = readAll(ex.getRequestBody());

            sb.append(divider("HTTP #" + id));
            sb.append(line("Quando",   now()));
            sb.append(line("Origem",   ex.getRemoteAddress().toString()));
            sb.append(line("Método",   ex.getRequestMethod()));
            sb.append(line("URI",      ex.getRequestURI().toString()));
            sb.append(line("Protocolo",ex.getProtocol()));

            sb.append("\n  Headers:\n");
            for (Map.Entry<String, List<String>> h : ex.getRequestHeaders().entrySet()) {
                sb.append("    ").append(h.getKey()).append(": ")
                  .append(String.join(", ", h.getValue())).append("\n");
            }

            sb.append("\n  Corpo (").append(body.length).append(" bytes):\n");
            appendBody(sb, body);
            sb.append(divider(null));

            print(sb.toString());

            // Resposta simples de confirmação.
            byte[] resp = ("OK - recebido pelo hardware-connection-tester (HTTP #" + id + ")\n")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, resp.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            print("[HTTP #" + id + "] erro ao processar requisição: " + e);
        } finally {
            ex.close();
        }
    }

    // ------------------------------------------------------------------- TCP

    private static void startTcpServer(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (true) {
                    Socket socket = serverSocket.accept();
                    long id = TCP_SEQ.incrementAndGet();
                    Thread conn = new Thread(() -> handleTcp(socket, id),
                            "tcp-conn-" + id);
                    conn.setDaemon(true);
                    conn.start();
                }
            } catch (IOException e) {
                print("[TCP] servidor encerrado: " + e);
            }
        }, "tcp-listener");
        t.setDaemon(true);
        t.start();
    }

    private static void handleTcp(Socket socket, long id) {
        String origem = socket.getRemoteSocketAddress().toString();
        print(divider("TCP #" + id + " CONECTADO")
                + line("Quando", now())
                + line("Origem", origem)
                + divider(null));

        try (InputStream in = socket.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            long chunk = 0;
            while ((n = in.read(buf)) != -1) {
                chunk++;
                byte[] data = new byte[n];
                System.arraycopy(buf, 0, data, 0, n);

                StringBuilder sb = new StringBuilder();
                sb.append(divider("TCP #" + id + " DADOS (bloco " + chunk + ")"));
                sb.append(line("Quando", now()));
                sb.append(line("Origem", origem));
                sb.append(line("Bytes",  String.valueOf(n)));
                sb.append("\n");
                appendBody(sb, data);
                sb.append(divider(null));
                print(sb.toString());
            }
        } catch (IOException e) {
            print("[TCP #" + id + "] erro de leitura: " + e);
        } finally {
            print("[TCP #" + id + "] DESCONECTADO (" + origem + ")");
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // -------------------------------------------------------------- Helpers

    /** Imprime o corpo como texto UTF-8 e também em hex dump (útil p/ binário). */
    private static void appendBody(StringBuilder sb, byte[] body) {
        if (body.length == 0) {
            sb.append("    <vazio>\n");
            return;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        for (String l : text.split("\n", -1)) {
            sb.append("    | ").append(l).append("\n");
        }
        sb.append("\n    Hex dump:\n");
        sb.append(hexDump(body));
    }

    /** Hex dump estilo `hexdump -C`: offset, 16 bytes em hex, e ASCII. */
    private static String hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 16) {
            sb.append(String.format("    %08X  ", i));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    int b = data[i + j] & 0xFF;
                    sb.append(String.format("%02X ", b));
                    ascii.append(b >= 32 && b < 127 ? (char) b : '.');
                } else {
                    sb.append("   ");
                }
                if (j == 7) sb.append(' ');
            }
            sb.append(" |").append(ascii).append("|\n");
        }
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    private static String line(String label, String value) {
        return "  " + label + ": " + value + "\n";
    }

    private static String divider(String title) {
        if (title == null) {
            return "=".repeat(72) + "\n";
        }
        String bar = "=".repeat(72);
        return "\n" + bar + "\n  " + title + "\n" + bar + "\n";
    }

    /** System.out sincronizado para não misturar logs de threads diferentes. */
    private static synchronized void print(String s) {
        System.out.print(s);
        System.out.flush();
    }

    private static void banner(int httpPort, int tcpPort) {
        String host = "0.0.0.0";
        print("""

                #############################################################
                #  Hardware Connection Tester                               #
                #  Recebe qualquer comunicação e loga tudo no console.      #
                #############################################################
                """);
        print("  HTTP catch-all : http://" + host + ":" + httpPort + "/  (qualquer método/rota/content-type)\n");
        print("  TCP bruto      : " + host + ":" + tcpPort + "  (socket puro, qualquer byte)\n");
        print("  Aguardando conexões... (Ctrl+C para parar)\n");
    }
}
