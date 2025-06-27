package com.mycompany.practica2;

import java.io.*;
import java.net.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Servidor_UDP {

    public static void main(String[] args) throws IOException {
        int pto = 1234;
        DatagramSocket s = new DatagramSocket(pto);
        s.setReuseAddress(true);

        System.out.println("Servidor iniciado... esperando datagramas...");
        
        for (;;) {
            // --- Esperar nombre del archivo ---
            byte[] bufferN = new byte[1024];
            DatagramPacket nom = new DatagramPacket(bufferN, bufferN.length);
            s.receive(nom);
            String nombreArchivo = new String(nom.getData(), 0, nom.getLength());
            System.out.println("Nombre recibido: " + nombreArchivo);

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream("C:\\Users\\lopez\\Documents\\ServidorUDP\\" + nombreArchivo);

                // --- Recibir datos del archivo ---
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket paq = new DatagramPacket(buffer, buffer.length);
                    s.receive(paq);

                    byte[] data = paq.getData();
                    int secuencia = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    
                    System.out.println("Secuencia recibida: " + secuencia);

                    if (secuencia == -1) { // Secuencia especial para fin
                        System.out.println("Fin de archivo detectado.");
                        break;
                    }

                    fos.write(data, 4, paq.getLength() - 4); // Empieza a escribir después de los 4 bytes de secuencia

                    // Enviar ACK
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write((secuencia >> 24) & 0xFF);
                    baos.write((secuencia >> 16) & 0xFF);
                    baos.write((secuencia >> 8) & 0xFF);
                    baos.write(secuencia & 0xFF);
                    DatagramPacket ack = new DatagramPacket(baos.toByteArray(), baos.size(), paq.getAddress(), paq.getPort());
                    s.send(ack);

                    System.out.println("ACK enviado para secuencia: " + secuencia);
                }
            } catch (IOException e) {
                System.out.println("Error de escritura: " + e.getMessage());
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }

            // --- Si era ZIP, descomprimir ---
            if (nombreArchivo.endsWith(".zip")) {
                File archivoZip = new File("C:\\Users\\lopez\\Documents\\ServidorUDP\\" + nombreArchivo);
                File des = Descomprimir(archivoZip, "C:\\Users\\lopez\\Documents\\ServidorUDP\\");

                if (des != null) {
                    if (!archivoZip.delete()) {
                        System.out.println("Error al eliminar el archivo ZIP.");
                    }
                } else {
                    System.out.println("Hubo un error al descomprimir.");
                }
            }

            System.out.println("Archivo recibido correctamente.\n");
        }
    }

    public static File Descomprimir(File archivoZip, String destino) {
        File carpetaDestino = new File(destino);
        // System.out.println("carpetaDestino: " + carpetaDestino.getAbsolutePath());
        if (!carpetaDestino.exists()) {
            carpetaDestino.mkdirs();  // Crea la carpeta si no existe
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archivoZip))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File archivoNuevo = new File(carpetaDestino, zipEntry.getName());

                // Si es una carpeta, la crea
                if (zipEntry.isDirectory()) {
                    archivoNuevo.mkdirs();
                } else {
                    // Crea las carpetas padre si no existen
                    archivoNuevo.getParentFile().mkdirs();

                    // Escribe el archivo
                    try (FileOutputStream fos = new FileOutputStream(archivoNuevo)) {
                        byte[] buffer = new byte[1024];
                        int bytesLeidos;
                        while ((bytesLeidos = zis.read(buffer)) >= 0) {
                            fos.write(buffer, 0, bytesLeidos);
                        }
                    }
                }
                zis.closeEntry();
            }
            // System.out.println("Descompresión completa en: " + carpetaDestino.getAbsolutePath());
            return carpetaDestino;  // Retorna la carpeta descomprimida

        } catch (IOException e) {
            System.out.println("Error al descomprimir -> " + e.getMessage());
            return null;
        }
    }
}
