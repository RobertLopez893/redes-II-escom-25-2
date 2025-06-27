// Cliente NIO completo usando SocketChannel para canal de control y datos
package com.mycompany.practica6;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Cliente_C {

    static class ClienteContext {
        String actual = "C:\\Users\\lopez\\Documents\\Cliente";
        SocketChannel control;
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        Charset charset = Charset.defaultCharset();
        BufferedReader entrada;
        PrintWriter salida;
    }

    public static void main(String[] args) {
        try {
            ClienteContext ctx = new ClienteContext();
            ctx.control = SocketChannel.open();
            ctx.control.connect(new InetSocketAddress("localhost", 2121));
            ctx.control.configureBlocking(true);

            ReadableByteChannel controlIn = Channels.newChannel(ctx.control.socket().getInputStream());
            WritableByteChannel controlOut = Channels.newChannel(ctx.control.socket().getOutputStream());

            System.out.println("Conectado al servidor NIO.");
            Scanner sc = new Scanner(System.in);

            while (true) {
                System.out.print("ftp> ");
                String linea = sc.nextLine().trim();
                if (linea.equals("quit")) break;
                String[] partes = linea.split(" ", 3);
                String prim = partes[0];
                String arg = partes.length > 1 ? partes[1] : "";
                String arg2 = partes.length > 2 ? partes[2] : "";

                switch (prim) {
                    case "put" -> subirArchivo(ctx, arg);
                    case "get" -> recibirArchivo(ctx, arg);
                    case "lls" -> listarLocal(ctx);
                    case "lcd" -> cambiarLocal(ctx, arg);
                    case "lrename" -> renombrarLocal(ctx, arg, arg2);
                    case "ldelete" -> borrarLocal(ctx, arg);
                    case "lrmdir" -> borrarDirLocal(ctx, arg);
                    case "lmkdir" -> crearDirLocal(ctx, arg);
                    case "lmkfile" -> crearArchivoLocal(ctx, arg);
                    case "help" -> mostrarAyuda();
                    default -> ejecutarRemoto(ctx, linea, controlIn, controlOut);
                }
            }

            ctx.control.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void ejecutarRemoto(ClienteContext ctx, String comando, ReadableByteChannel in, WritableByteChannel out) throws IOException {
        out.write(Charset.defaultCharset().encode(comando + "\n"));
        ByteBuffer buf = ByteBuffer.allocate(4096);
        while (in.read(buf) > 0) {
            buf.flip();
            String resp = ctx.charset.decode(buf).toString();
            System.out.print(resp);
            if (resp.contains("226") || resp.contains("250") || resp.contains("550") || resp.contains("500")) break;
            buf.clear();
        }
    }

    static void subirArchivo(ClienteContext ctx, String nombre) {
        try {
            File archivo = new File(ctx.actual, nombre);
            if (!archivo.exists()) {
                System.out.println("No existe el archivo.");
                return;
            }
            boolean comprimido = false;
            if (archivo.isDirectory()) {
                archivo = Comprimir(archivo.getAbsolutePath(), ctx.actual);
                comprimido = true;
            }

            SocketChannel canal = SocketChannel.open(new InetSocketAddress("localhost", 2020));
            canal.configureBlocking(true);

            String cabecera = "PUT " + archivo.getName() + " " + archivo.length() + "\n";
            canal.write(ctx.charset.encode(cabecera));

            try (FileChannel fc = FileChannel.open(archivo.toPath(), StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (fc.read(buffer) > 0) {
                    buffer.flip();
                    canal.write(buffer);
                    buffer.clear();
                }
            }
            canal.close();
            System.out.println("Archivo enviado.");
            if (comprimido) archivo.delete();
        } catch (IOException e) {
            System.out.println("Error al subir archivo: " + e.getMessage());
        }
    }

    static void recibirArchivo(ClienteContext ctx, String nombre) {
        try {
            SocketChannel canal = SocketChannel.open(new InetSocketAddress("localhost", 2020));
            canal.configureBlocking(true);
            canal.write(ctx.charset.encode("GET " + nombre + "\n"));

            Path destino = Path.of(ctx.actual, nombre);
            try (WritableByteChannel out = Files.newByteChannel(destino, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (canal.read(buffer) > 0) {
                    buffer.flip();
                    out.write(buffer);
                    buffer.clear();
                }
            }
            canal.close();
            System.out.println("Archivo recibido.");

            if (nombre.endsWith(".zip")) {
                File des = Descomprimir(destino.toFile(), ctx.actual);
                destino.toFile().delete();
            }
        } catch (IOException e) {
            System.out.println("Error al recibir archivo: " + e.getMessage());
        }
    }

    static void listarLocal(ClienteContext ctx) {
        File carpeta = new File(ctx.actual);
        System.out.println("Directorio local actual: " + carpeta.getAbsolutePath());
        for (File f : Objects.requireNonNull(carpeta.listFiles())) {
            System.out.println((f.isDirectory() ? "[Carpeta] " : "[Archivo] ") + f.getName());
        }
    }

    static void cambiarLocal(ClienteContext ctx, String dir) {
        File nueva = new File(dir);
        if (!nueva.isAbsolute()) nueva = new File(ctx.actual, dir);
        if (nueva.exists() && nueva.isDirectory()) {
            ctx.actual = nueva.getAbsolutePath();
            System.out.println("Ruta local cambiada a: " + ctx.actual);
        } else {
            System.out.println("No se pudo cambiar.");
        }
    }

    static void renombrarLocal(ClienteContext ctx, String ant, String nuevo) {
        File fa = new File(ctx.actual, ant);
        File fb = new File(ctx.actual, nuevo);
        if (!fa.exists()) System.out.println("Archivo no existe.");
        else if (fb.exists()) System.out.println("Ya existe el nuevo nombre.");
        else if (fa.renameTo(fb)) System.out.println("Renombrado.");
        else System.out.println("Error al renombrar.");
    }

    static void borrarLocal(ClienteContext ctx, String nombre) {
        File f = new File(ctx.actual, nombre);
        if (f.isFile() && f.delete()) System.out.println("Archivo borrado.");
        else System.out.println("No se pudo borrar archivo.");
    }

    static void borrarDirLocal(ClienteContext ctx, String nombre) {
        File dir = new File(ctx.actual, nombre);
        if (dir.exists() && dir.isDirectory() && eliminarRec(dir))
            System.out.println("Directorio eliminado.");
        else System.out.println("Error eliminando directorio.");
    }

    static boolean eliminarRec(File dir) {
        File[] archivos = dir.listFiles();
        if (archivos != null) for (File f : archivos)
            if (f.isDirectory()) eliminarRec(f);
            else f.delete();
        return dir.delete();
    }

    static void crearDirLocal(ClienteContext ctx, String nombre) {
        File d = new File(ctx.actual, nombre);
        if (!d.exists() && d.mkdirs()) System.out.println("Directorio creado.");
        else System.out.println("Error creando directorio.");
    }

    static void crearArchivoLocal(ClienteContext ctx, String nombre) {
        File f = new File(ctx.actual, nombre);
        try {
            if (f.createNewFile()) System.out.println("Archivo creado.");
            else System.out.println("Ya existe.");
        } catch (IOException e) {
            System.out.println("Error creando archivo.");
        }
    }

    static void mostrarAyuda() {
        System.out.println("Comandos disponibles:");
        System.out.println("put <archivo> - Subir archivo al servidor");
        System.out.println("get <archivo> - Descargar archivo del servidor");
        System.out.println("ls, cd, mkdir, delete, etc. - Comandos remotos");
        System.out.println("lls, lcd, lrename, ldelete, lrmdir, lmkdir, lmkfile - Comandos locales");
        System.out.println("quit - Salir del cliente");
    }

    public static File Comprimir(String carpetaOrigen, String actual) {
        File carpeta = new File(carpetaOrigen);
        String nombreZip = carpeta.getName() + ".zip";
        File archivoZip = new File(actual, nombreZip);
        try (FileOutputStream fos = new FileOutputStream(archivoZip); ZipOutputStream zos = new ZipOutputStream(fos)) {
            comprimirCarpeta(carpeta, carpeta.getName(), zos);
            return archivoZip;
        } catch (IOException e) {
            System.out.println("Error al comprimir: " + e.getMessage());
            return null;
        }
    }

    private static void comprimirCarpeta(File carpeta, String base, ZipOutputStream zos) throws IOException {
        for (File archivo : Objects.requireNonNull(carpeta.listFiles())) {
            String rutaZip = base + "/" + archivo.getName();
            if (archivo.isDirectory()) {
                comprimirCarpeta(archivo, rutaZip, zos);
            } else {
                try (FileInputStream fis = new FileInputStream(archivo)) {
                    zos.putNextEntry(new ZipEntry(rutaZip));
                    byte[] buffer = new byte[1024];
                    int leidos;
                    while ((leidos = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, leidos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    public static File Descomprimir(File archivoZip, String destino) {
        File destinoDir = new File(destino);
        if (!destinoDir.exists()) destinoDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archivoZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File nuevo = new File(destinoDir, entry.getName());
                if (entry.isDirectory()) nuevo.mkdirs();
                else {
                    nuevo.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(nuevo)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
            return destinoDir;
        } catch (IOException e) {
            System.out.println("Error al descomprimir: " + e.getMessage());
            return null;
        }
    }
}
