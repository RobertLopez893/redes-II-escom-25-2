package com.mycompany.practica1;

import java.io.*;
import java.net.*;
import java.util.zip.*;

public class Servidor_C {

    public static void main(String[] args) {
        try {
            ServerSocket controlS = new ServerSocket(2121);
            ServerSocket datosS = new ServerSocket(2020);
            controlS.setReuseAddress(true);
            System.out.println("Servidor de control conectado, esperando cliente...");

            for (;;) {
                Socket cl = controlS.accept();
                System.out.println("Conexión exitosa.");

                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(cl.getInputStream()));
                    PrintWriter writer = new PrintWriter(cl.getOutputStream(), true);

                    String cmd;
                    String actual = "C:\\Users\\lopez\\Documents\\Servidor";

                    while ((cmd = reader.readLine()) != null) {
                        System.out.println("Recibí: " + cmd);

                        String[] partes = cmd.split(" ", 3);
                        String prim = partes[0];
                        String arg = (partes.length > 1) ? partes[1] : "";
                        String arg2 = (partes.length > 2) ? partes[2] : "";

                        switch (prim) {
                            case "ls" -> {
                                // Listar archivos remotos.
                                writer.println(listar(actual));
                                writer.println("226 Listado exitoso.");
                            }

                            case "cd" -> {
                                // Cambiar de directorio remoto.
                                String nuevoDir = cambiar(actual, arg);

                                if (nuevoDir != null) {
                                    actual = nuevoDir;
                                    writer.println("250 Directorio cambiado a: " + actual);
                                } else {
                                    writer.println("Error 550: Directorio no encontrado.");
                                }
                            }

                            case "rename" -> // Renombrar archivo o directorio remoto.
                                writer.println(renombrar(actual, arg, arg2));

                            case "delete" -> // Borrar archivo remoto.
                                writer.println(borrarArchivo(actual, arg));

                            case "rmdir" -> // Borrar directorio remoto.
                                writer.println(borrarDir(actual, arg));

                            case "mkdir" -> // Crear directorio remoto.
                                writer.println(crearDir(actual, arg));

                            case "mkfile" -> // Crear archivo remoto.
                                writer.println(crearArchivo(actual, arg));

                            case "put" -> { // Recibir archivo local.
                                try (Socket cl2 = datosS.accept()) {
                                    System.out.println("Socket de datos listo.");

                                    String nombre;
                                    long tam;
                                    File ar = new File(actual, arg);
                                    String ruta = ar.getAbsolutePath();
                                    System.out.println("Ruta antes del dis: " + actual + "\\" + arg);
                                    ar.setWritable(true);
                                    String abs;

                                    try (DataInputStream dis = new DataInputStream(cl2.getInputStream())) {
                                        System.out.println("Vamos en el dis.");

                                        try {
                                            nombre = dis.readUTF();
                                            tam = dis.readLong();
                                        } catch (IOException e) {
                                            System.out.println("Error: " + e);
                                            break;
                                        }

                                        System.out.println("Nombre recibido: " + nombre);
                                        System.out.println("Tamaño recibido: " + tam);

                                        if (nombre.endsWith(".zip")) {
                                            abs = new File(ar.getParent(), nombre).getAbsolutePath();
                                        } else {
                                            abs = ruta;
                                        }

                                        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(abs))) {
                                            System.out.println("Empiezo a recibir.");
                                            long recibidos = 0;

                                            while (recibidos < tam) {
                                                System.out.println("Recibiendo datos...");
                                                byte[] b = new byte[4096];
                                                int leidos = dis.read(b);
                                                if (leidos == -1) {
                                                    break;
                                                }

                                                dos.write(b, 0, leidos);
                                                dos.flush();
                                                recibidos += leidos;
                                                System.out.println("Leídos: " + leidos);
                                                System.out.println("Recibidos: " + recibidos);
                                            }

                                            System.out.println("Recibí.");
                                            System.out.println(dos);
                                            System.out.println("Recibidos: " + recibidos);
                                            System.out.println("El tamaño: " + tam);
                                            System.out.println(dis);
                                        }
                                    }

                                    if (nombre.endsWith(".zip")) {
                                        File archivoZip = new File(ar.getParent(), nombre);
                                        File des = Descomprimir(archivoZip, actual);

                                        if (des != null) {
                                            if (!archivoZip.delete()) {
                                                System.out.println("Error al eliminar el archivo ZIP.");
                                            }
                                        } else {
                                            System.out.println("Hubo un error al descomprimir.");
                                        }
                                    }
                                    System.out.println("He recibido el archivo correctamente.");
                                    cl2.close();
                                }
                            }

                            case "get" -> { // Enviar archivo remoto.
                                try (Socket cl2 = datosS.accept()) {
                                    File ar = new File(actual + "\\" + arg);
                                    boolean comprimio = false;

                                    if (ar.isDirectory()) {
                                        boolean a1 = ar.canRead();
                                        boolean a2 = ar.canWrite();
                                        boolean a3 = ar.canExecute();

                                        System.out.println(a1);
                                        System.out.println(a2);
                                        System.out.println(a3);

                                        File comprimido = Comprimir(actual + "\\" + arg, actual);

                                        if (comprimido != null) {
                                            ar = comprimido;
                                            comprimio = true;
                                            System.out.println("Comprimido.");
                                        } else {
                                            System.out.println("Error al comprimir el archivo.");
                                            break;
                                        }
                                    }

                                    String nombre = ar.getName();
                                    String ruta = ar.getAbsolutePath();
                                    long tam = ar.length();

                                    try (DataOutputStream dos = new DataOutputStream(cl2.getOutputStream()); DataInputStream dis = new DataInputStream(new FileInputStream(ruta))) {

                                        System.out.println("Entre en el dos.");
                                        System.out.println("Mi tam es " + tam);
                                        dos.writeUTF(nombre);
                                        dos.flush();
                                        dos.writeLong(tam);
                                        dos.flush();

                                        System.out.println(dos);

                                        long enviados = 0;

                                        while (enviados < tam) {
                                            System.out.println("Enviando paquetes...");
                                            byte[] b = new byte[4096];
                                            int bytesRead;

                                            while ((bytesRead = dis.read(b)) != -1) {
                                                dos.write(b, 0, bytesRead);
                                                dos.flush();
                                                enviados += bytesRead;
                                            }
                                        }

                                        System.out.println("Archivo enviado al cliente correctamente.");
                                        dis.close();
                                        dos.close();

                                        if (comprimio) {
                                            if (ar.delete()) {
                                                System.out.println("He borrado el archivo.");
                                            } else {
                                                System.out.println("No puedo borrar el archivo.");
                                            }
                                        }

                                        cl2.close();
                                    }

                                } catch (Exception e) {
                                    System.out.println("Error: " + e);
                                }

                            }

                            default ->
                                System.out.println("Comando no reconocido.");
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error: " + e);
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
