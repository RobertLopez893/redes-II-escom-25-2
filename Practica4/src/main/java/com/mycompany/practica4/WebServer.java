package com.mycompany.practica4;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {

    public static final int PUERTO = 8000;
    ServerSocket ss;

    class Manejador implements Runnable {

        protected Socket socket;
        protected PrintWriter pw;
        protected BufferedOutputStream bos;
        protected BufferedReader br;
        protected String FileName;

        public Manejador(Socket _socket) throws Exception {
            this.socket = _socket;
        }

        @Override
        public void run() {
            try {
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bos = new BufferedOutputStream(socket.getOutputStream());
                pw = new PrintWriter(new OutputStreamWriter(bos));
                String line = br.readLine();

                if (line == null) {
                    sendHTMLResponse("Linea vacía", 200);
                    socket.close();
                    return;
                }

                System.out.println("\nCliente Conectado desde: " + socket.getInetAddress());
                System.out.println("Por el puerto: " + socket.getPort());
                System.out.println("Datos: " + line + "\r\n");

                String method = line.split(" ")[0];
                String path = line.split(" ")[1].replace("/", "");

                switch (method.toUpperCase()) {
                    case "GET" -> {
                        if (path.contains("?")) {
                            StringTokenizer tokens = new StringTokenizer(path, "?");
                            String req = tokens.nextToken();
                            sendHTMLResponse("<h1>Parámetros obtenidos:</h1><h3>" + req + "</h3>", 200);
                        } else {
                            if (path.equals("")) {
                                path = "index.htm";
                            }

                            File archivo = new File(path);
                            if (!archivo.exists()) {
                                sendHTMLResponse("<h1>404 Not Found</h1><p>El archivo no existe: " + path + "</p>", 404);
                            } else {
                                SendA(path); // Envia con tipo MIME correcto
                            }
                        }
                    }

                    case "POST" -> {
                        // Leer contenido del cuerpo (hasta línea vacía)
                        while (!br.readLine().isEmpty()) {
                        }
                        char[] buffer = new char[1024];
                        int len = br.read(buffer);
                        String postData = new String(buffer, 0, len);
                        try (PrintWriter outPost = new PrintWriter(new FileWriter("post_data.txt", true))) {
                            outPost.println(postData);
                        }
                        sendHTMLResponse("<h1>POST recibido</h1><p>" + postData + "</p>", 200);
                    }

                    case "PUT" -> {
                        while (!br.readLine().isEmpty()) {
                        }
                        char[] bufferPut = new char[1024];
                        int lenPut = br.read(bufferPut);
                        String putData = new String(bufferPut, 0, lenPut);
                        try (PrintWriter outPut = new PrintWriter(new FileWriter(path))) {
                            outPut.println(putData);
                        }
                        sendHTMLResponse("<h1>Archivo creado/modificado con PUT</h1>", 200);
                    }

                    case "DELETE" -> {
                        File f = new File(path);
                        if (f.exists()) {
                            f.delete();
                            sendHTMLResponse("<h1>Archivo eliminado</h1>", 200);
                        } else {
                            sendHTMLResponse("<h1>Archivo no encontrado</h1>", 404);
                        }
                    }

                    default ->
                        sendHTMLResponse("<h1>501 Not Implemented</h1>", 501);
                }

                pw.flush();
                bos.flush();

            } catch (IOException XD) {
                System.out.println("Error: " + XD);
            }

            try {
                socket.close();
            } catch (IOException XD) {
                System.out.println("Error: " + XD);
            }
        }

        public void getArch(String line) {
            int i;
            int f;
            if (line.toUpperCase().startsWith("GET")) {
                i = line.indexOf("/");
                f = line.indexOf(" ", i);
                FileName = line.substring(i + 1, f);
            }
        }

        public void SendA(String fileName, Socket sc) {
            byte[] buffer = new byte[4096];
            try {
                DataOutputStream out = new DataOutputStream(sc.getOutputStream());

                try ( 
                        FileInputStream f = new FileInputStream(fileName)) {
                    int x = 0;
                    while ((x = f.read(buffer)) > 0) {
                        //		System.out.println(x);
                        out.write(buffer, 0, x);
                    }
                    out.flush();
                }
            } catch (FileNotFoundException XD) {
                System.out.println("Error con archivo: " + XD);
            } catch (IOException XD) {
                System.out.println("Error: " + XD);
            }

        }

        public void SendA(String arg) {
            try {
                File archivo = new File(arg);
                int tam_archivo = (int) archivo.length();
                try (BufferedInputStream bis2 = new BufferedInputStream(new FileInputStream(archivo))) {
                    byte[] buf = new byte[1024];
                    
                    // Detectar el tipo MIME según la extensión
                    String contentType = guessMimeType(arg);
                    
                    String sb = "";
                    sb += "HTTP/1.0 200 OK\r\n";
                    sb += "Server: Rober Server/1.0\r\n";
                    sb += "Date: " + new Date() + "\r\n";
                    sb += "Content-Type: " + contentType + "\r\n";
                    sb += "Content-Length: " + tam_archivo + "\r\n";
                    sb += "\r\n";
                    
                    System.out.println("---- CABECERA HTTP ENVIADA ----");
                    System.out.println(sb);
                    System.out.println("--------------------------------");
                    
                    bos.write(sb.getBytes());
                    bos.flush();
                    
                    int b_leidos;
                    while ((b_leidos = bis2.read(buf, 0, buf.length)) != -1) {
                        bos.write(buf, 0, b_leidos);
                    }
                    bos.flush();
                }

            } catch (IOException XD) {
                System.out.println(XD.getMessage());
            }
        }

        private void sendHTMLResponse(String html, int statusCode) {
            String statusLine = "HTTP/1.0 " + statusCode + " "
                    + (statusCode == 200 ? "OK" : (statusCode == 404 ? "Not Found" : "Error"));

            String headers = statusLine + "\r\n"
                    + "Server: Rober Server/1.0\r\n"
                    + "Content-Type: text/html\r\n"
                    + "Content-Length: " + html.length() + "\r\n"
                    + "\r\n";

            try {
                bos.write(headers.getBytes());
                bos.write(html.getBytes());
                bos.flush();

                // Mostrar en terminal
                System.out.println("---- CABECERA HTTP ENVIADA ----");
                System.out.println(headers);
                System.out.println("--------------------------------");
            } catch (IOException XD) {
                System.out.println("Error: " + XD);
            }
        }
    }

    public WebServer() throws Exception {
        System.out.println("Iniciando Servidor.......");
        this.ss = new ServerSocket(PUERTO);
        System.out.println("Servidor iniciado:---OK");
        System.out.println("Esperando por Cliente....");
        ExecutorService pool = Executors.newFixedThreadPool(10); // pool con 10 hilos
        for (;;) {
            Socket accept = ss.accept();
            pool.execute(new Manejador(accept)); // usa pool en vez de crear hilo
        }
    }

    private String guessMimeType(String fileName) {
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".css")) {
            return "text/css";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript";
        }
        if (fileName.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream"; // por defecto
    }

    public static void main(String[] args) throws Exception {
        WebServer sWEB = new WebServer();
    }
}
