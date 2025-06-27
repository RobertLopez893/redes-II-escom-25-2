package com.mycompany.practica6;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Servidor_C {

    static class TransferContext {

        enum Modo {
            NONE, PUT, GET
        }
        Modo modo = Modo.NONE;
        String nombre;
        long tam = -1;
        long progreso = 0;
        FileChannel archivo;
        ByteBuffer buffer = ByteBuffer.allocate(8192);
    }

    public static void main(String[] args) {
        try {
            Selector selector = Selector.open();

            ServerSocketChannel controlChannel = ServerSocketChannel.open();
            controlChannel.socket().bind(new InetSocketAddress(2121));
            controlChannel.configureBlocking(false);
            controlChannel.register(selector, SelectionKey.OP_ACCEPT);

            ServerSocketChannel dataChannel = ServerSocketChannel.open();
            dataChannel.socket().bind(new InetSocketAddress(2020));
            dataChannel.configureBlocking(false);
            dataChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Servidor NIO activo en puertos 2121 (control) y 2020 (datos)");

            while (true) {
                selector.select();
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);

                        if (server == controlChannel) {
                            client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(4096));
                            System.out.println("Cliente de control conectado.");
                        } else {
                            client.register(selector, SelectionKey.OP_READ, new TransferContext());
                            System.out.println("Cliente de datos conectado.");
                        }
                    } else if (key.isReadable()) {
                        switch (key.attachment()) {
                            case ByteBuffer buffer -> {
                                SocketChannel client = (SocketChannel) key.channel();
                                try {
                                    int read = client.read(buffer);
                                    if (read == -1) {
                                        client.close();
                                        continue;
                                    }
                                    buffer.flip();
                                    String cmd = Charset.defaultCharset().decode(buffer).toString().trim();
                                    buffer.clear();
                                    
                                    System.out.println("Comando recibido: " + cmd);
                                    String[] partes = cmd.split(" ", 3);
                                    String prim = partes[0];
                                    String arg = partes.length > 1 ? partes[1] : "";
                                    String arg2 = partes.length > 2 ? partes[2] : "";
                                    String actual = "C:\\Users\\lopez\\Documents\\Servidor";
                                    String respuesta;
                                    
                                    switch (prim) {
                                        case "ls" ->
                                            respuesta = listar(actual) + "\n226 Listado exitoso.";
                                        case "cd" -> {
                                            String nuevo = cambiar(actual, arg);
                                            respuesta = nuevo != null ? "250 Directorio cambiado a: " + nuevo : "550 Error.";
                                        }
                                        case "rename" ->
                                            respuesta = renombrar(actual, arg, arg2);
                                        case "delete" ->
                                            respuesta = borrarArchivo(actual, arg);
                                        case "rmdir" ->
                                            respuesta = borrarDir(actual, arg);
                                        case "mkdir" ->
                                            respuesta = crearDir(actual, arg);
                                        case "mkfile" ->
                                            respuesta = crearArchivo(actual, arg);
                                        default ->
                                            respuesta = "500 Comando no reconocido.";
                                    }
                                    
                                    client.write(Charset.defaultCharset().encode(respuesta + "\n"));
                                } catch (IOException e) {
                                    key.cancel();
                                    client.close();
                                }
                            }
                            case TransferContext ctx -> {
                                SocketChannel ch = (SocketChannel) key.channel();
                                try {
                                    if (ctx.modo == TransferContext.Modo.NONE) {
                                        ctx.buffer.clear();
                                        int read = ch.read(ctx.buffer);
                                        if (read == -1) {
                                            ch.close();
                                            continue;
                                        }
                                        ctx.buffer.flip();
                                        String header = Charset.defaultCharset().decode(ctx.buffer).toString().trim();
                                        ctx.buffer.clear();
                                        
                                        if (header.startsWith("PUT ")) {
                                            ctx.modo = TransferContext.Modo.PUT;
                                            String[] p = header.split(" ");
                                            ctx.nombre = p[1];
                                            ctx.tam = Long.parseLong(p[2]);
                                            ctx.archivo = FileChannel.open(Path.of("C:\\Users\\lopez\\Documents\\Servidor", ctx.nombre),
                                                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                                        } else if (header.startsWith("GET ")) {
                                            ctx.modo = TransferContext.Modo.GET;
                                            ctx.nombre = header.split(" ")[1];
                                            File f = new File("C:\\Users\\lopez\\Documents\\Servidor", ctx.nombre);
                                            ctx.tam = f.length();
                                            ctx.archivo = new FileInputStream(f).getChannel();
                                            key.interestOps(SelectionKey.OP_WRITE);
                                        }
                                    } else if (ctx.modo == TransferContext.Modo.PUT) {
                                        ctx.buffer.clear();
                                        int read = ch.read(ctx.buffer);
                                        if (read == -1) {
                                            ch.close();
                                            ctx.archivo.close();
                                            continue;
                                        }
                                        ctx.buffer.flip();
                                        while (ctx.buffer.hasRemaining()) {
                                            ctx.archivo.write(ctx.buffer);
                                        }
                                        ctx.progreso += read;
                                        if (ctx.progreso >= ctx.tam) {
                                            ctx.archivo.close();
                                            ch.close();
                                            System.out.println("Archivo recibido: " + ctx.nombre);
                                        }
                                    }
                                } catch (IOException e) {
                                    key.cancel();
                                    ch.close();
                                }
                            }
                            default -> {
                            }
                        }
                    } else if (key.isWritable()) {
                        if (key.attachment() instanceof TransferContext ctx) {
                            SocketChannel ch = (SocketChannel) key.channel();
                            try {
                                ctx.buffer.clear();
                                int leidos = ctx.archivo.read(ctx.buffer);
                                if (leidos == -1) {
                                    ctx.archivo.close();
                                    ch.close();
                                    continue;
                                }
                                ctx.buffer.flip();
                                ch.write(ctx.buffer);
                                ctx.progreso += leidos;
                                if (ctx.progreso >= ctx.tam) {
                                    ctx.archivo.close();
                                    ch.close();
                                    System.out.println("Archivo enviado: " + ctx.nombre);
                                }
                            } catch (IOException e) {
                                key.cancel();
                                ch.close();
                            }
                        }
                    }
                    iter.remove();
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e);
        }
    }

    public static String listar(String ruta) {
        File actual = new File(ruta);

        File[] archivos = actual.listFiles();

        StringBuilder sb = new StringBuilder();

        if (archivos != null) {
            sb.append("Directorio remoto actual: ").append(ruta).append("\n");
            for (File a : archivos) {
                sb.append((a.isDirectory() ? "[Carpeta] " : "[Archivo] ")).append(a.getName()).append("\n");
            }
        }

        return sb.toString();
    }

    public static String cambiar(String actual, String nuevo) {
        File nuevaRuta = new File(nuevo);

        if (!nuevaRuta.isAbsolute()) {
            nuevaRuta = new File(actual, nuevo);
        }

        if (nuevaRuta.exists() && nuevaRuta.isDirectory()) {
            System.out.println("Ruta remota cambiada exitosamente a: " + nuevaRuta);
            return nuevaRuta.getAbsolutePath();
        }

        return null;
    }

    public static String renombrar(String actual, String antiguoNombre, String nuevoNombre) {
        File archivoAntiguo = new File(actual, antiguoNombre);
        File archivoNuevo = new File(actual, nuevoNombre);

        if (!archivoAntiguo.exists()) {
            return "550 Error: El archivo a renombrar no existe.";
        }

        if (archivoNuevo.exists()) {
            return "550 Error: Ya existe un archivo con el nuevo nombre.";
        }

        if (archivoAntiguo.renameTo(archivoNuevo)) {
            return "250 Archivo renombrado con éxito: " + antiguoNombre + " -> " + nuevoNombre;
        } else {
            return "501 Error: No se pudo renombrar el archivo.";
        }
    }

    public static String borrarArchivo(String actual, String ruta) {
        File archivo = new File(actual, ruta);

        if (!archivo.isFile()) {
            return "Este comando está solo reservado para archivos.";
        }

        if (archivo.delete()) {
            return "250 Archivo eliminado exitosamente.";
        } else {
            return "550 Error: No se pudo eliminar el archivo.";
        }

    }

    public static String borrarDir(String actual, String ruta) {
        File carpeta = new File(actual, ruta);

        if (!carpeta.exists()) {
            return "Error: El directorio no existe.";
        }

        if (!carpeta.isDirectory()) {
            return "Este comando está solo reservado para directorios.";

        }

        if (eliminarRec(carpeta)) {
            return "Directorio eliminado exitosamente.";
        } else {
            return "Error al eliminar el directorio.";
        }

    }

    private static boolean eliminarRec(File carpeta) {
        File[] archivos = carpeta.listFiles();

        if (archivos != null) {
            for (File ar : archivos) {
                if (ar.isDirectory()) {
                    if (!eliminarRec(ar)) {
                        return false;
                    }
                } else if (!ar.delete()) {
                    System.out.println("Error al eliminar archivo: " + ar.getAbsolutePath());
                    return false;
                }
            }
        }

        return carpeta.delete();
    }

    public static String crearDir(String actual, String nombre) {
        File nuevodir = new File(actual, nombre);

        if (!nuevodir.exists()) {
            if (nuevodir.mkdirs()) {
                return "Se ha creado el directorio " + nuevodir.getAbsolutePath();
            } else {
                return "Error al crear el directorio.";
            }
        } else {
            return "El directorio ya existe.";
        }
    }

    public static String crearArchivo(String actual, String nombre) {
        File archivo = new File(actual, nombre);

        try {
            if (archivo.createNewFile()) {
                return "Archivo creado: " + archivo.getAbsolutePath();
            } else {
                return "El archivo ya existe.";
            }
        } catch (IOException e) {
            return "Error: ";
        }
    }

    public static File Descomprimir(File archivoZip, String destino) {
        File carpetaDestino = new File(destino);

        System.out.println("Carpeta destino: " + carpetaDestino.getAbsolutePath());
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
            //System.out.println("Descompresión completa en: " + carpetaDestino.getAbsolutePath());
            return carpetaDestino;  // Retorna la carpeta descomprimida

        } catch (IOException e) {
            System.out.println("Error al descomprimir -> " + e.getMessage());
            return null;
        }
    }

    public static File Comprimir(String carpetaOrigen, String actual) {
        File carpeta = new File(carpetaOrigen);
        System.out.println("Carpeta name: " + carpeta.getAbsolutePath());
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.out.println("Error: La carpeta no existe o no es un directorio.");
            return null;
        }

        String nombreZip = carpeta.getName() + ".zip";
        System.out.println("NombreZip: " + nombreZip);
        File archivoZip = new File(actual, nombreZip);
        System.out.println("archivoZip: " + archivoZip.getAbsolutePath());

        try (
                FileOutputStream fos = new FileOutputStream(archivoZip); ZipOutputStream zos = new ZipOutputStream(fos)) {
            comprimirCarpeta(carpeta, carpeta.getName(), zos);
            zos.close();
            System.out.println("Carpeta comprimida con éxito en: " + archivoZip.getAbsolutePath());
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
