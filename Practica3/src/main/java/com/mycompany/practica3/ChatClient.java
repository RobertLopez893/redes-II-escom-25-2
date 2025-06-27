package com.mycompany.practica3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;

public class ChatClient {

    // Conexión al servidor
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;

    // Componentes de la interfaz gráfica
    private JFrame frame;
    private CardLayout cards;
    private JPanel contenedor;

    // Datos del usuario
    private JTextField campoNombre;
    private String nombreUsuario;

    // Salas disponibles
    private DefaultListModel<String> modeloSalas;
    private JList<String> listaSalas;

    // Interfaz de chat
    private JTextArea areaMensajes;
    private JTextField campoTexto;
    private JTextField campoPrivado;

    public ChatClient() {
        inicializarConexion();
        construirGUI();
        escucharServidor();
    }

    // Establece la conexión con el servidor principal del chat
    private void inicializarConexion() {
        try {
            socket = new Socket("127.0.0.1", 2000);
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "No se pudo conectar con el servidor.");
            System.exit(1);
        }
    }

    // Crea la interfaz gráfica y organiza los paneles
    private void construirGUI() {
        frame = new JFrame("Chat");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        contenedor = new JPanel();
        cards = new CardLayout();
        contenedor.setLayout(cards);

        contenedor.add(pantallaNombre(), "PANEL_NOMBRE");
        contenedor.add(pantallaSalas(), "PANEL_SALAS");
        contenedor.add(pantallaChat(), "PANEL_CHAT");

        frame.add(contenedor);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Panel inicial donde el usuario ingresa su nombre
    private JPanel pantallaNombre() {
        JPanel panel = new JPanel(new GridBagLayout());
        campoNombre = new JTextField(15);
        JButton boton = new JButton("Entrar");

        boton.addActionListener(e -> {
            nombreUsuario = campoNombre.getText().trim();
            if (!nombreUsuario.isEmpty()) {
                pw.println(nombreUsuario);
            }
        });

        panel.add(new JLabel("Ingresa tu nombre de usuario:"));
        panel.add(campoNombre);
        panel.add(boton);
        return panel;
    }

    // Panel para listar, crear o unirse a salas
    private JPanel pantallaSalas() {
        JPanel panel = new JPanel(new BorderLayout());

        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        panel.add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        JPanel botones = new JPanel();
        JButton crear = new JButton("Crear sala");
        JButton unirse = new JButton("Unirse");
        JButton actualizar = new JButton("Actualizar");
        JButton salir = new JButton("Salir");

        // Crear nueva sala
        crear.addActionListener(e -> {
            String nombre = JOptionPane.showInputDialog(frame, "Nombre de la sala:");
            if (nombre != null && !nombre.trim().isEmpty()) {
                pw.println("CREAR_SALA " + nombre.trim());
            }
        });

        // Unirse a la sala seleccionada
        unirse.addActionListener(e -> {
            String sala = listaSalas.getSelectedValue();
            if (sala != null) {
                pw.println("ENTRAR_SALA " + sala);
            }
        });

        // Salir del cliente
        salir.addActionListener(e -> {
            pw.println("SALIR");
            System.exit(0);
        });

        // Solicitar actualización de lista de salas
        actualizar.addActionListener(e -> pw.println("LISTAR_SALAS"));

        botones.add(crear);
        botones.add(unirse);
        botones.add(salir);
        botones.add(actualizar);

        panel.add(botones, BorderLayout.SOUTH);

        return panel;
    }

    // Panel principal del chat
    private JPanel pantallaChat() {
        JPanel panel = new JPanel(new BorderLayout());

        areaMensajes = new JTextArea();
        areaMensajes.setEditable(false);
        areaMensajes.setLineWrap(true);
        areaMensajes.setWrapStyleWord(true);
        panel.add(new JScrollPane(areaMensajes), BorderLayout.CENTER);

        JPanel entrada = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        campoTexto = new JTextField(20);
        campoPrivado = new JTextField(10);
        JButton enviar = new JButton("Enviar");
        JButton volver = new JButton("Volver");
        JButton enviarArchivo = new JButton("Archivo");
        JButton descargarArchivo = new JButton("Descargar");

        // Enviar mensaje (público o privado)
        enviar.addActionListener(e -> {
            String msg = campoTexto.getText().trim();
            if (!msg.isEmpty()) {
                if (!campoPrivado.getText().trim().isEmpty()) {
                    pw.println("MENSAJE_PRIVADO " + campoPrivado.getText().trim() + " " + msg);
                } else {
                    pw.println("MENSAJE_PUBLICO " + msg);
                }
                areaMensajes.append("Yo: " + reemplazarEmojis(msg) + "\n");
                campoTexto.setText("");
            }
        });

        // Volver al panel de salas
        volver.addActionListener(e -> {
            pw.println("LISTAR_SALAS");
            cards.show(contenedor, "PANEL_SALAS");
        });

        // Seleccionar y enviar archivo
        enviarArchivo.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int resultado = fileChooser.showOpenDialog(frame);
            if (resultado == JFileChooser.APPROVE_OPTION) {
                File archivo = fileChooser.getSelectedFile();
                new Thread(() -> enviarArchivo(archivo)).start();
            }
        });

        // Descargar archivo ingresando su nombre
        descargarArchivo.addActionListener(e -> {
            String nombreArchivo = JOptionPane.showInputDialog(frame, "Nombre del archivo a descargar:");
            if (nombreArchivo != null && !nombreArchivo.trim().isEmpty()) {
                new Thread(() -> descargarArchivo(nombreArchivo.trim())).start();
            }
        });

        // Disposición de los componentes
        gbc.gridx = 0;
        gbc.gridy = 0;
        entrada.add(new JLabel("Mensaje:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        entrada.add(campoTexto, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        entrada.add(new JLabel("Privado a:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        entrada.add(campoPrivado, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        entrada.add(enviar, gbc);
        gbc.gridx = 3;
        gbc.gridy = 1;
        entrada.add(volver, gbc);
        gbc.gridx = 4;
        gbc.gridy = 1;
        entrada.add(enviarArchivo, gbc);
        gbc.gridx = 5;
        gbc.gridy = 1;
        entrada.add(descargarArchivo, gbc);

        panel.add(entrada, BorderLayout.SOUTH);

        return panel;
    }

    // Escucha los mensajes que envía el servidor al cliente
    private void escucharServidor() {
        Thread lector = new Thread(() -> {
            try {
                String linea;
                while ((linea = br.readLine()) != null) {
                    if (linea.startsWith("PEDIR_NOMBRE")) {
                        cards.show(contenedor, "PANEL_NOMBRE");

                    } else if (linea.startsWith("OK_NOMBRE")) {
                        pw.println("LISTAR_SALAS");

                    } else if (linea.startsWith("LISTA_SALAS")) {
                        modeloSalas.clear();
                        String[] partes = linea.split(" ", 2);
                        if (partes.length > 1) {
                            String[] salas = partes[1].split(",");
                            Arrays.stream(salas).forEach(modeloSalas::addElement);
                        }
                        frame.setTitle("Salas - " + nombreUsuario);
                        cards.show(contenedor, "PANEL_SALAS");

                    } else if (linea.startsWith("OK_ENTRAR_SALA")) {
                        areaMensajes.setText("");
                        pw.println("LISTAR_USUARIOS");
                        frame.setTitle("Chat - " + nombreUsuario);
                        cards.show(contenedor, "PANEL_CHAT");

                    } else if (linea.startsWith("LISTA_USUARIOS")) {
                        areaMensajes.append("[Usuarios en sala]:\n");
                        String[] partes = linea.split(" ", 2);
                        if (partes.length > 1) {
                            String[] usuarios = partes[1].split(",");
                            for (String u : usuarios) {
                                areaMensajes.append("- " + u + "\n");
                            }
                        }

                    } else if (linea.startsWith("OK_SALA_CREADA")) {
                        pw.println("LISTAR_SALAS");

                    } else if (linea.startsWith("ERROR_")) {
                        JOptionPane.showMessageDialog(frame, "Servidor: " + linea);

                    } else if (!linea.startsWith("OK_MENSAJE_PUBLICO") && !linea.startsWith("OK_MENSAJE_PRIVADO")) {
                        areaMensajes.append(reemplazarEmojis(linea) + "\n");
                    }
                }
            } catch (IOException e) {
                areaMensajes.append("Conexión cerrada.\n");
            }
        });
        lector.start();
    }

    // Envía archivo al servidor
    private void enviarArchivo(File archivo) {
        try (Socket socketArchivo = new Socket("127.0.0.1", 2020);
             DataOutputStream dos = new DataOutputStream(socketArchivo.getOutputStream());
             FileInputStream fis = new FileInputStream(archivo)) {

            String nombre = archivo.getName();
            long tam = archivo.length();

            dos.writeUTF(nombre);
            dos.writeLong(tam);

            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, leidos);
            }

            SwingUtilities.invokeLater(() -> areaMensajes.append("[Archivo enviado]: " + nombre + "\n"));

            pw.println("MENSAJE_PUBLICO [📎Archivo enviado📎]: " + nombre);

        } catch (IOException ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, "Error al enviar archivo: " + ex.getMessage())
            );
        }
    }

    // Sustituye símbolos por emojis
    private String reemplazarEmojis(String mensaje) {
        return mensaje
                .replace(":)", "😊")
                .replace(":(", "😞")
                .replace(":o", "😮")
                .replace(":D", "😃")
                .replace(";)", "😉")
                .replace("<3", "❤️")
                .replace(":P", "😛")
                .replace("XD", "😆")
                .replace(">:(", "😠")
                .replace("7w7", "😏")
                .replace("D:", "😦");
    }

    // Descarga archivo desde el servidor, preguntando al usuario la ubicación de guardado
    private void descargarArchivo(String nombreArchivo) {
        try (Socket socketArchivo = new Socket("127.0.0.1", 2020);
             DataOutputStream dos = new DataOutputStream(socketArchivo.getOutputStream());
             DataInputStream dis = new DataInputStream(socketArchivo.getInputStream())) {

            dos.writeUTF("DESCARGAR");
            dos.writeUTF(nombreArchivo);

            boolean existe = dis.readBoolean();
            if (!existe) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "El archivo no existe en el servidor.")
                );
                return;
            }

            long tam = dis.readLong();

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(nombreArchivo));
            int seleccion = fileChooser.showSaveDialog(frame);
            if (seleccion != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File archivoDestino = fileChooser.getSelectedFile();

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                byte[] buffer = new byte[4096];
                long recibidos = 0;
                int leidos;
                while (recibidos < tam && (leidos = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, leidos);
                    recibidos += leidos;
                }
            }

            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, "Archivo descargado en: " + archivoDestino.getAbsolutePath())
            );

        } catch (IOException ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, "Error al descargar archivo: " + ex.getMessage())
            );
        }
    }

    // Punto de entrada del programa cliente
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::new);
    }
}