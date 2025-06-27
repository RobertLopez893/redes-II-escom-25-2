package com.mycompany.practica1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

public class Cliente_C {

    public static void main(String[] args) {
        try {
            try (Socket controlC = new Socket("localhost", 2121)) {
                System.out.println("Conexión con el servidor exitosa.");

                try (Scanner sc = new Scanner(System.in)) {
                    // String actual = System.getProperty("user.dir");
                    String actual = "C:\\Users\\lopez\\Documents\\Cliente";
                    PrintWriter writer = new PrintWriter(controlC.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(controlC.getInputStream()));

                    while (true) {
                        System.out.print("ftp> ");

                        String comando = sc.nextLine().trim();

                        if (comando.equals("quit")) {
                            break;
                        }

                        String[] partes = comando.split(" ", 3);
                        String prim = partes[0];
                        String arg = (partes.length > 1) ? partes[1] : "";
                        String arg2 = (partes.length > 2) ? partes[2] : "";

                        switch (prim) {
                            case "lls" -> // Listar carpeta local.
                                listar(actual);

                            case "ls" -> {
                                // Listar carpeta remota.
                                writer.println("ls");
                                String res;

                                while ((res = reader.readLine()) != null) {
                                    System.out.println(res);
                                    if (res.startsWith("226")) {
                                        break;
                                    }
                                }
                            }

                            case "lcd" -> {
                                // Cambiar de directorio local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    actual = cambiar(actual, arg);
                                }
                            }

                            case "cd" -> {
                                // Cambiar de directorio remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("cd " + arg);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "rename" -> {
                                // Cambiar el nombre del archivo/directorio remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Se necesita el nombre del archivo a renombrar.");
                                } else if (arg2.isEmpty()) {
                                    System.out.println("Error: Ingrese el nuevo nombre del archivo.");
                                } else {
                                    writer.println("rename " + arg + " " + arg2);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "lrename" -> {
                                // Cambiar el nombre del archivo/directorio local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Se necesita el nombre del archivo a renombrar.");
                                } else if (arg2.isEmpty()) {
                                    System.out.println("Error: Ingrese el nuevo nombre del archivo.");
                                } else {
                                    renombrar(actual, arg, arg2);
                                }
                            }

                            case "delete" -> {
                                // Borrar archivo remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("delete " + arg);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "ldelete" -> {
                                // Borrar archivo local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    borrarArchivo(actual, arg);
                                }
                            }

                            case "rmdir" -> {
                                // Borrar directorio remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("rmdir " + arg);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "lrmdir" -> {
                                // Borrar directorio local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    borrarDir(actual, arg);
                                }
                            }

                            case "mkdir" -> {
                                // Crea directorio remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("mkdir " + arg);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "lmkdir" -> {
                                // Crea directorio local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    crearDir(actual, arg);
                                }
                            }

                            case "mkfile" -> {
                                // Crea archivo remoto.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("mkfile " + arg);
                                    System.out.println(reader.readLine());
                                }
                            }

                            case "lmkfile" -> {
                                // Crea archivo local.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    crearArchivo(actual, arg);
                                }
                            }

                            case "put" -> {
                                // Subir archivo al servidor.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    writer.println("put " + arg);
                                    boolean comprimio = false;

                                    try (Socket datos = new Socket("localhost", 2020)) {
                                        File ar = new File(actual + "\\" + arg);

                                        if (ar.isDirectory()) {
                                            if (ar.listFiles() != null && ar.listFiles().length > 0) {
                                                File comprimido = Comprimir(actual + "\\" + arg, actual);

                                                if (comprimido != null) {
                                                    ar = comprimido;
                                                    // System.out.println("Comprimido. Ar = " + ar + " Comprimido = " + comprimido);
                                                    comprimio = true;
                                                } else {
                                                    System.out.println("Error al comprimir el archivo.");
                                                    break;
                                                }
                                            }
                                        }

                                        String nombre = ar.getName();
                                        String ruta = ar.getAbsolutePath();
                                        long tam = ar.length();

                                        /*
                                        System.out.println("Nombre: " + nombre);
                                        System.out.println("Ruta: " + ruta);
                                        System.out.println("Tamaño: " + tam);
                                         */
                                        try (DataOutputStream dos = new DataOutputStream(datos.getOutputStream()); DataInputStream dis = new DataInputStream(new FileInputStream(ruta))) {
                                            // System.out.println("Mi tamaño es " + tam);
                                            dos.writeUTF(nombre);
                                            dos.flush();
                                            dos.writeLong(tam);
                                            dos.flush();

                                            // System.out.println(dos);
                                            long enviados = 0;

                                            while (enviados < tam) {
                                                // System.out.println("La ruta es: " + ruta);
                                                // System.out.println("Enviando paquetes...");
                                                byte[] b = new byte[4096];
                                                int bytesRead;

                                                while ((bytesRead = dis.read(b)) != -1) {
                                                    dos.write(b, 0, bytesRead);
                                                    dos.flush();
                                                    enviados += bytesRead;
                                                    // System.out.println("Enviados: " + enviados);
                                                }
                                            }

                                            System.out.println("Archivo enviado al servidor correctamente.");
                                            dis.close();
                                            dos.close();

                                            if (comprimio) {
                                                if (ar.delete()) {
                                                    // System.out.println("He borrado el archivo.");
                                                } else {
                                                    // System.out.println("No puedo borrar el archivo.");
                                                }
                                            }

                                            datos.close();
                                        }
                                    } catch (Exception e) {
                                        System.out.println("Error: " + e);
                                    }
                                }

                            }

                            case "get" -> {
                                // Descargar archivo del servidor.
                                if (arg.isEmpty()) {
                                    System.out.println("Error: Argumento faltante.");
                                } else {
                                    if (arg.isEmpty()) {
                                        System.out.println("Error: Argumento faltante.");
                                    } else {
                                        writer.println("get " + arg);

                                        try (Socket datos = new Socket("localhost", 2020)) {
                                            File ar = new File(actual, arg);
                                            String ruta = ar.getAbsolutePath();
                                            ar.setWritable(true);

                                            String nombre;
                                            String abs;
                                            long tam;

                                            try (DataInputStream dis = new DataInputStream(datos.getInputStream())) {
                                                // System.out.println("Vamos en el dis.");
                                                try {
                                                    nombre = dis.readUTF();
                                                    tam = dis.readLong();
                                                } catch (IOException e) {
                                                    System.out.println("Error: " + e);
                                                    break;
                                                }

                                                if (nombre.endsWith(".zip")) {
                                                    abs = new File(ar.getParent(), nombre).getAbsolutePath();
                                                } else {
                                                    abs = ruta;
                                                }

                                                try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(abs))) {
                                                    // System.out.println("Empiezo a recibir.");
                                                    long recibidos = 0;

                                                    while (recibidos < tam) {
                                                        // System.out.println("Recibiendo datos...");
                                                        byte[] b = new byte[4096];
                                                        int leidos = dis.read(b);
                                                        if (leidos == -1) {
                                                            break;
                                                        }

                                                        dos.write(b, 0, leidos);
                                                        dos.flush();
                                                        recibidos += leidos;
                                                    }

                                                    /*
                                                    System.out.println("Recibí.");
                                                    System.out.println(dos);
                                                    System.out.println("Recibidos: " + recibidos);
                                                    System.out.println("El tamaño: " + tam);
                                                    System.out.println(dis);*/
                                                    dis.close();
                                                    dos.close();

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
                                                    datos.close();
                                                }
                                            }

                                        } catch (Exception e) {
                                            System.out.println("Error: " + e);
                                        }
                                    }
                                }
                            }

                            case "clear" -> {
                                for (int i = 0; i < 50; i++) {
                                    System.out.println();
                                }
                            }

                            case "help" -> {
                                System.out.println("lls - Lista la carpeta local actual.\nls - Lista la carpeta remota actual.\nlcd - Cambia el directorio local.\ncd - Cambia el directorio remoto.");
                                System.out.println("lrename - Renombra un archivo/directorio local.\nrename - Renombra un archivo/diretorio remoto.\nldelete - Borra un archivo local.\ndelete - Borra un archivo remoto.");
                                System.out.println("lrmdir - Elimina un directorio local.\nrmdir - Elimina un directorio remoto.\nlmkdir - Crea un directorio local.\nmkdir - Crea un directorio remoto.");
                                System.out.println("lmkfile - Crea un archivo local.\nmkfile - Crea un archivo remoto.\nput - Sube un archivo/directorio al servidor.\nget - Descarga un archivo/directorio del servidor.");
                                System.out.println("help - Lista los comandos de la aplicación.\nclear - 'Limpia' la pantalla.\nquit - Finalizar la sesión ftp y salir.");
                            }

                            default ->
                                System.out.println("Comando no reconocido.");
                        }

                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e);
        }
    }

    public static void listar(String ruta) {
        File actual = new File(ruta);

        File[] archivos = actual.listFiles();

        if (archivos != null) {
            System.out.println("Directorio local actual: " + ruta);
            for (File a : archivos) {
                System.out.println((a.isDirectory() ? "[Carpeta] " : "[Archivo] ") + a.getName());
            }
        }
    }

    public static String cambiar(String actual, String nuevo) {
        File nuevaRuta = new File(nuevo);

        if (!nuevaRuta.isAbsolute()) {
            nuevaRuta = new File(actual, nuevo);
        }

        if (nuevaRuta.exists() && nuevaRuta.isDirectory()) {
            System.out.println("Ruta local cambiada exitosamente a: " + nuevaRuta);
            return nuevaRuta.getAbsolutePath();
        }

        return null;
    }

    public static void renombrar(String actual, String antiguoNombre, String nuevoNombre) {
        File archivoAntiguo = new File(actual, antiguoNombre);
        File archivoNuevo = new File(actual, nuevoNombre);

        if (!archivoAntiguo.exists()) {
            System.out.println("Error: El archivo a renombrar no existe.");
            return;
        }

        if (archivoNuevo.exists()) {
            System.out.println("Error: Ya existe un archivo con el nuevo nombre.");
            return;
        }

        if (archivoAntiguo.renameTo(archivoNuevo)) {
            System.out.println("Archivo renombrado con éxito: " + antiguoNombre + " -> " + nuevoNombre);
        } else {
            System.out.println("Error: No se pudo renombrar el archivo.");
        }
    }

    public static void borrarArchivo(String actual, String ruta) {
        File archivo = new File(actual, ruta);
        // Scanner sc = new Scanner(System.in);

        if (!archivo.isFile()) {
            System.out.println("Este comando está solo reservado para archivos.");
            return;
        }

        // System.out.println("¿Está seguro que quiere eliminar " + ruta + "? Y/N");

        // if (sc.nextLine().equals("Y")) {
            archivo.delete();
            System.out.println("Archivo eliminado exitosamente.");
        // }
    }

    public static void borrarDir(String actual, String ruta) {
        File carpeta = new File(actual, ruta);
        // Scanner sc = new Scanner(System.in);

        if (!carpeta.exists()) {
            System.out.println("Error: El directorio no existe.");
            return;
        }

        if (!carpeta.isDirectory()) {
            System.out.println("Este comando está solo reservado para directorios.");
            return;
        }

        // System.out.println("¿Está seguro que quiere eliminar " + ruta + "? Y/N");

        // if (sc.nextLine().equals("Y")) {
            if (eliminarRec(carpeta)) {
                System.out.println("Directorio eliminado exitosamente.");
            } else {
                System.out.println("Error al eliminar el directorio.");
            }
        // }
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

    public static void crearDir(String actual, String nombre) {
        File nuevodir = new File(actual, nombre);

        if (!nuevodir.exists()) {
            if (nuevodir.mkdirs()) {
                System.out.println("Se ha creado el directorio " + nuevodir.getAbsolutePath());
            } else {
                System.out.println("Error al crear el directorio.");
            }
        } else {
            System.out.println("El directorio ya existe.");
        }
    }

    public static void crearArchivo(String actual, String nombre) {
        File archivo = new File(actual, nombre);

        try {
            if (archivo.createNewFile()) {
                System.out.println("Archivo creado: " + archivo.getAbsolutePath());
            } else {
                System.out.println("El archivo ya existe.");
            }
        } catch (IOException e) {
            System.out.println("Error: ");
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
