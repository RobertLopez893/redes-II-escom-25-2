package com.mycompany.practica3;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {

    // Mapa global que asocia nombres de salas con objetos Sala
    private static Map<String, Sala> salas = new HashMap<>();

    public static void main(String[] args) {
        try (
            // Socket principal para mensajes del chat
            ServerSocket servidor = new ServerSocket(2000);
            // Socket secundario para transferencia de archivos
            ServerSocket servidorArchivos = new ServerSocket(2020)
        ) {
            servidor.setReuseAddress(true);
            servidorArchivos.setReuseAddress(true);

            System.out.println("Servidor de chat en puerto 2000.");
            System.out.println("Servidor de archivos en puerto 2020.");

            // Hilo dedicado para manejar subida y descarga de archivos
            new Thread(() -> {
                while (true) {
                    try {
                        Socket clArchivo = servidorArchivos.accept();
                        new Thread(() -> Sala.manejarConexionArchivo(clArchivo)).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            // Bucle principal: acepta conexiones de clientes del chat
            while (true) {
                Socket cl = servidor.accept();
                new Thread(new Manejador(cl)).start(); // Crea un hilo por cliente
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Clase interna para manejar la lógica individual de cada cliente
    static class Manejador implements Runnable {

        private Socket cl;
        private Sala salaActual;
        private String nombreUsuario;
        private BufferedReader br;
        private PrintWriter pw;

        public Manejador(Socket cl) {
            this.cl = cl;
        }

        public void run() {
            try {
                // Flujo de entrada y salida del cliente
                br = new BufferedReader(new InputStreamReader(cl.getInputStream(), "UTF-8"));
                pw = new PrintWriter(new OutputStreamWriter(cl.getOutputStream(), "UTF-8"), true);

                // Solicitar nombre de usuario
                pw.println("PEDIR_NOMBRE");
                nombreUsuario = br.readLine();

                if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                    pw.println("ERROR_NOMBRE_INVALIDO");
                    cl.close();
                    return;
                }

                pw.println("OK_NOMBRE " + nombreUsuario);

                // Bucle principal de escucha de comandos desde el cliente
                String comando;
                while ((comando = br.readLine()) != null) {

                    // Enviar lista de salas disponibles
                    if (comando.equals("LISTAR_SALAS")) {
                        if (salas.isEmpty()) {
                            pw.println("LISTA_SALAS");
                        } else {
                            String lista = String.join(",", salas.keySet());
                            pw.println("LISTA_SALAS " + lista);
                        }

                    // Crear una nueva sala
                    } else if (comando.startsWith("CREAR_SALA")) {
                        String[] partes = comando.split(" ", 2);
                        if (partes.length < 2) {
                            pw.println("ERROR_SALA_SIN_NOMBRE");
                            continue;
                        }
                        String nombreSala = partes[1];

                        if (salas.containsKey(nombreSala)) {
                            pw.println("ERROR_SALA_YA_EXISTE");
                        } else {
                            salas.put(nombreSala, new Sala(nombreSala));
                            pw.println("OK_SALA_CREADA " + nombreSala);
                        }

                    // Unirse a una sala existente
                    } else if (comando.startsWith("ENTRAR_SALA")) {
                        String[] partes = comando.split(" ", 2);
                        if (partes.length < 2) {
                            pw.println("ERROR_SALA_SIN_NOMBRE");
                            continue;
                        }
                        String nombreSala = partes[1];
                        Sala sala = salas.get(nombreSala);

                        if (sala != null) {
                            if (salaActual != null) {
                                salaActual.eliminarCliente(this);
                            }
                            sala.agregarCliente(this);
                            salaActual = sala;
                            pw.println("OK_ENTRAR_SALA " + nombreSala);
                        } else {
                            pw.println("ERROR_SALA_NO_EXISTE");
                        }

                    // Enviar mensaje público a todos los de la sala
                    } else if (comando.startsWith("MENSAJE_PUBLICO")) {
                        if (salaActual != null) {
                            String mensaje = comando.substring("MENSAJE_PUBLICO".length()).trim();
                            salaActual.broadcast(nombreUsuario + ": " + mensaje, this);
                            pw.println("OK_MENSAJE_PUBLICO");
                        } else {
                            pw.println("ERROR_NO_SALA");
                        }

                    // Enviar mensaje privado a un usuario específico
                    } else if (comando.startsWith("MENSAJE_PRIVADO")) {
                        if (salaActual != null) {
                            String[] partes = comando.split(" ", 3);
                            if (partes.length < 3) {
                                pw.println("ERROR_COMANDO_PRIVADO");
                                continue;
                            }
                            String destino = partes[1];
                            String mensaje = partes[2];
                            salaActual.enviarPrivado(destino, nombreUsuario + " [privado]: " + mensaje, this);
                        } else {
                            pw.println("ERROR_NO_SALA");
                        }

                    // Listar usuarios en la sala actual
                    } else if (comando.equals("LISTAR_USUARIOS")) {
                        if (salaActual != null) {
                            salaActual.listarUsuarios(this);
                        } else {
                            pw.println("ERROR_NO_SALA");
                        }

                    // Salir del servidor
                    } else if (comando.equals("SALIR")) {
                        if (salaActual != null) {
                            salaActual.eliminarCliente(this);
                        }
                        pw.println("OK_SALIR");
                        break;

                    // Comando no reconocido
                    } else {
                        pw.println("ERROR_COMANDO_DESCONOCIDO");
                    }
                }

                cl.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Método para enviar mensajes al cliente
        public void enviar(String mensaje) {
            pw.println(mensaje);
        }

        public String getNombre() {
            return nombreUsuario;
        }
    }

    // Clase que representa una sala de chat con múltiples clientes
    static class Sala {

        private final String nombre;
        private final Set<Manejador> clientes = new HashSet<>();

        public Sala(String nombre) {
            this.nombre = nombre;
        }

        // Agrega cliente a la sala
        public synchronized void agregarCliente(Manejador c) {
            clientes.add(c);
            broadcast("[Servidor]: " + c.getNombre() + " se ha unido a la sala.", c);
        }

        // Elimina cliente de la sala
        public synchronized void eliminarCliente(Manejador c) {
            clientes.remove(c);
            broadcast("[Servidor]: " + c.getNombre() + " ha salido de la sala.", c);
        }

        // Envía mensaje a todos menos al remitente
        public synchronized void broadcast(String mensaje, Manejador remitente) {
            for (Manejador c : clientes) {
                if (c != remitente) {
                    c.enviar(mensaje);
                }
            }
        }

        // Envía mensaje privado a un destinatario
        public synchronized void enviarPrivado(String destinatario, String mensaje, Manejador remitente) {
            boolean encontrado = false;
            for (Manejador c : clientes) {
                if (c.getNombre().equals(destinatario)) {
                    c.enviar(mensaje);
                    remitente.enviar("OK_MENSAJE_PRIVADO");
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado) {
                remitente.enviar("ERROR_USUARIO_NO_ENCONTRADO");
            }
        }

        // Enlista los usuarios conectados en la sala
        public synchronized void listarUsuarios(Manejador solicitante) {
            List<String> nombres = new ArrayList<>();
            for (Manejador c : clientes) {
                nombres.add(c.getNombre());
            }
            solicitante.enviar("LISTA_USUARIOS " + String.join(",", nombres));
        }

        // Manejo de subida o descarga de archivos desde clientes
        public static void manejarConexionArchivo(Socket cl) {
            try (
                DataInputStream dis = new DataInputStream(cl.getInputStream());
                DataOutputStream dos = new DataOutputStream(cl.getOutputStream())
            ) {
                String operacion = dis.readUTF(); // "DESCARGAR" o nombre de archivo si se está subiendo

                if (operacion.equals("DESCARGAR")) {
                    // Descargar archivo solicitado
                    String nombreArchivo = dis.readUTF();
                    File archivo = new File("archivos_recibidos", nombreArchivo);

                    if (!archivo.exists()) {
                        dos.writeBoolean(false); // Archivo no existe
                        return;
                    }

                    dos.writeBoolean(true); // Confirmar existencia
                    dos.writeLong(archivo.length());

                    try (FileInputStream fis = new FileInputStream(archivo)) {
                        byte[] buffer = new byte[4096];
                        int leidos;
                        while ((leidos = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, leidos);
                        }
                    }

                    System.out.println("Archivo enviado: " + nombreArchivo);
                } else {
                    // Se está subiendo un archivo al servidor
                    String nombre = operacion;
                    long tam = dis.readLong();

                    File carpeta = new File("archivos_recibidos");
                    if (!carpeta.exists()) carpeta.mkdirs();

                    File archivo = new File(carpeta, nombre);
                    try (FileOutputStream fos = new FileOutputStream(archivo)) {
                        byte[] buffer = new byte[4096];
                        long recibidos = 0;
                        int leidos;
                        while (recibidos < tam && (leidos = dis.read(buffer)) != -1) {
                            fos.write(buffer, 0, leidos);
                            recibidos += leidos;
                        }
                    }

                    System.out.println("Archivo recibido: " + nombre);
                }

            } catch (IOException e) {
                System.out.println("Error en servidor de archivos: " + e.getMessage());
            }
        }
    }
}