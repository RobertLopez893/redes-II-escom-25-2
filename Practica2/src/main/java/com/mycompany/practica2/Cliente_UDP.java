package com.mycompany.practica2;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Cliente_UDP {

    public static void main(String[] args) throws SocketException, UnknownHostException, IOException {
        DatagramSocket c = new DatagramSocket();
        int pto = 1234;
        InetAddress dst = InetAddress.getByName("localhost");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        for (;;) {
            System.out.print("Escriba el nombre del archivo a enviar y 'quit' para abandonar el programa: ");
            String ruta = "C:\\Users\\lopez\\Documents\\ClienteUDP";
            String archivo = br.readLine();

            if (archivo.equals("quit")) {
                System.out.println("Proceso finalizado.");
                br.close();
                c.close();
                System.exit(0);
            } else {
                File ar = new File(ruta + "\\" + archivo);

                if (ar.isDirectory()) {
                    File comprimido = Comprimir(ruta + "\\" + archivo, ruta);

                    if (comprimido != null) {
                        ar = comprimido;
                    } else {
                        System.out.println("Error al comprimir el archivo.");
                        break;
                    }
                }

                System.out.println("Archivo a enviar: " + ar.getAbsolutePath());
                System.out.println("Nombre enviado: " + ar.getName());

                byte[] name = ar.getName().getBytes();
                DatagramPacket nom = new DatagramPacket(name, name.length, dst, pto);
                c.send(nom);
                System.out.println("Nombre del archivo enviado al servidor.");

                try (FileInputStream fis = new FileInputStream(ar)) {
                    byte[] buffer = new byte[1024];
                    List<byte[]> paquetes = new ArrayList<>();
                    int bytes;
                    int sec = 0;
                    int tamVentana = 3;
                    int base = 0;
                    int sig = 0;

                    while ((bytes = fis.read(buffer)) != -1) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.write((sec >> 24) & 0xFF); // Byte más significativo
                        baos.write((sec >> 16) & 0xFF);
                        baos.write((sec >> 8) & 0xFF);
                        baos.write(sec & 0xFF);         // Byte menos significativo
                        baos.write(buffer, 0, bytes);

                        byte[] paquete = baos.toByteArray();
                        paquetes.add(paquete);

                        System.out.println("Paquete guardado #" + sec + " | Bytes: " + bytes);
                        sec++;
                    }

                    for (byte[] paq : paquetes) {
                        System.out.println("Paquete: " + paq);
                    }

                    while (base < paquetes.size()) {
                        while (sig < base + tamVentana && sig < paquetes.size()) {
                            DatagramPacket paq = new DatagramPacket(paquetes.get(sig), paquetes.get(sig).length, dst, pto);
                            c.send(paq);
                            System.out.println("Enviado paquete " + sig);
                            sig++;
                        }
                        c.setSoTimeout(500);
                        try {
                            for (;;) {
                                byte[] bufferAck = new byte[4];
                                DatagramPacket ack = new DatagramPacket(bufferAck, bufferAck.length);
                                c.receive(ack);

                                int num = ((bufferAck[0] & 0xFF) << 24)
                                        | ((bufferAck[1] & 0xFF) << 16)
                                        | ((bufferAck[2] & 0xFF) << 8)
                                        | (bufferAck[3] & 0xFF);

                                System.out.println("Acuse recibido: " + num);

                                if (num >= base) {
                                    base = num + 1;
                                    System.out.println("Base: " + base);
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("Error de TimeOut: " + e);
                            System.out.println("Reintentando...");
                            for (int i = base; i < sig; i++) {
                                DatagramPacket paq = new DatagramPacket(paquetes.get(i), paquetes.get(i).length, dst, pto);
                                c.send(paq);
                                System.out.println("Enviando nuevamente el paquete " + i);
                            }
                        }
                    }

                    ByteArrayOutputStream fin = new ByteArrayOutputStream();
                    fin.write((-1 >> 24) & 0xFF);
                    fin.write((-1 >> 16) & 0xFF);
                    fin.write((-1 >> 8) & 0xFF);
                    fin.write(-1 & 0xFF);
                    byte[] finBytes = fin.toByteArray();
                    c.send(new DatagramPacket(finBytes, finBytes.length, dst, pto));

                    System.out.println("Señal de fin de archivo enviada.");
                    System.out.println("Total de paquetes enviados: " + sec);
                    fis.close();
                }
            }

        }
    }

    public static File Comprimir(String carpetaOrigen, String actual) {
        File carpeta = new File(carpetaOrigen);
        // System.out.println("Carpeta name: " + carpeta.getAbsolutePath());
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.out.println("Error: La carpeta no existe o no es un directorio.");
            return null;
        }

        String nombreZip = carpeta.getName() + ".zip";
        // System.out.println("NombreZip: " + nombreZip);
        File archivoZip = new File(actual, nombreZip);
        // System.out.println("archivoZip: " + archivoZip.getAbsolutePath());

        try (
                FileOutputStream fos = new FileOutputStream(archivoZip); ZipOutputStream zos = new ZipOutputStream(fos)) {
            comprimirCarpeta(carpeta, carpeta.getName(), zos);
            zos.close();
            // System.out.println("Carpeta comprimida con éxito en: " + archivoZip.getAbsolutePath());
            return archivoZip;

        } catch (IOException e) {
            System.out.println("Error al comprimir -> " + e.getMessage());
            return null;
        }
    }

    private static void comprimirCarpeta(File carpeta, String nombreBase, ZipOutputStream zos) throws IOException {
        File[] archivos = carpeta.listFiles();
        if (archivos == null) {
            return;
        }
        for (File archivo : archivos) {
            String rutaZip = nombreBase + "/" + archivo.getName();

            if (archivo.isDirectory()) {
                comprimirCarpeta(archivo, rutaZip, zos);
            } else {
                try (FileInputStream fis = new FileInputStream(archivo)) {
                    ZipEntry zipEntry = new ZipEntry(rutaZip);
                    zos.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    int bytesLeidos;
                    while ((bytesLeidos = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, bytesLeidos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

}
