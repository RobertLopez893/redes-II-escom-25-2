package com.mycompany.practica5;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WebClient {

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Pattern LINK_PATTERN = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_PATTERN = Pattern.compile("src=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static ExecutorService threadPool;
    private static final Queue<String> urlQueue = new ConcurrentLinkedQueue<>();
    private static final Set<String> downloadedUrls = ConcurrentHashMap.newKeySet();
    private static final Set<String> failedUrls = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger totalAttempts = new AtomicInteger(0);
    private static int maxDepth = 10;  // Profundidad por defecto
    private static String baseUrl;
    private static final String outputDir = "Descargas";
    private static String siteFolderName;
    private static JTextArea resultArea;
    private static JTextField urlInput;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Crear la interfaz gráfica
            JFrame frame = new JFrame("Aplicación WGET");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 400);

            // Panel para ingresar la URL y los botones
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            // Caja de texto para URL con un tamaño más pequeño
            urlInput = new JTextField(20);
            panel.add(new JLabel("Ingrese la URL a descargar:"));
            panel.add(urlInput);

            // Botón para iniciar la descarga
            JButton downloadButton = new JButton("Iniciar descarga");
            downloadButton.addActionListener(e -> iniciarDescarga());
            panel.add(downloadButton);

            // Área de texto para mostrar resultados
            resultArea = new JTextArea(10, 40);
            resultArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(resultArea);
            panel.add(scrollPane);

            frame.add(panel);
            frame.setVisible(true);
        });
    }

    private static void iniciarDescarga() {
        // Obtener la URL del campo de texto
        baseUrl = urlInput.getText().trim();
        if (baseUrl.isEmpty()) {
            resultArea.append("Por favor ingrese una URL válida.\n");
            return;
        }

        try {
            URL urlObj = new URL(baseUrl);
            siteFolderName = urlObj.getHost();
            if (siteFolderName.startsWith("www.")) {
                siteFolderName = siteFolderName.substring(4);
            }
        } catch (MalformedURLException e) {
            resultArea.append("URL inválida: " + e.getMessage() + "\n");
            return;
        }

        resultArea.append("Iniciando descarga de: " + baseUrl + "\n");

        try {
            Files.createDirectories(Paths.get(outputDir, siteFolderName));
        } catch (IOException e) {
            resultArea.append("Error al crear el directorio de salida: " + e.getMessage() + "\n");
            return;
        }

        // Valores por defecto para hilos y profundidad
        int threadCount = 25;
        maxDepth = 10;

        threadPool = Executors.newFixedThreadPool(threadCount);
        urlQueue.add(baseUrl + "|0");

        while (!urlQueue.isEmpty() || getActiveCount() > 0) {
            String urlWithDepth = urlQueue.poll();
            if (urlWithDepth != null) {
                threadPool.execute(() -> processUrl(urlWithDepth));
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        resultArea.append("=== Resumen Final ===\n");
        resultArea.append("Total de archivos intentados: " + totalAttempts.get() + "\n");
        resultArea.append("Descargas exitosas: " + successCount.get() + "\n");
    }

    private static int getActiveCount() {
        if (threadPool instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getActiveCount();
        }
        return 0;
    }

    private static void processUrl(String urlWithDepth) {
        String[] parts = urlWithDepth.split("\\|");
        String url = parts[0];
        int depth = Integer.parseInt(parts[1]);

        if (downloadedUrls.contains(url)) {
            return;
        }

        downloadedUrls.add(url);
        totalAttempts.incrementAndGet();

        try {
            resultArea.append("Descargando: " + url + "\n");

            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                String filePath = getLocalFilePath(url);
                File outputFile = new File(filePath);

                outputFile.getParentFile().mkdirs();

                try (InputStream inputStream = connection.getInputStream(); FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                resultArea.append("Guardado como: " + filePath + "\n");
                successCount.incrementAndGet();

                String contentType = connection.getContentType();
                boolean isHtml = (contentType != null && contentType.contains("text/html"));

                if (depth < maxDepth && (url.endsWith(".html") || url.endsWith(".htm") || isHtml)) {
                    String htmlContent = new String(Files.readAllBytes(outputFile.toPath()));
                    processLinks(htmlContent, url, depth);
                    ensureHtmlExtension(outputFile);
                }

                String htmlContent = new String(Files.readAllBytes(outputFile.toPath()));
                String modifiedContent = rewriteLinks(htmlContent, url, filePath);
                Files.write(outputFile.toPath(), modifiedContent.getBytes());
            } else {
                resultArea.append("Error al descargar " + url + " - Código: " + responseCode + "\n");
                failedUrls.add(url + " (Código: " + responseCode + ")");
            }
        } catch (IOException e) {
            resultArea.append("Error al procesar " + url + ": " + e.getMessage() + "\n");
            failedUrls.add(url + " (Error: " + e.getMessage() + ")");
        }
    }

    private static void ensureHtmlExtension(File file) throws IOException {
        String path = file.getAbsolutePath();
        if (!path.endsWith(".html") && !path.endsWith(".htm")) {
            File newFile = new File(path + ".html");
            if (file.renameTo(newFile)) {
                resultArea.append("Renombrado a: " + newFile.getPath() + "\n");
            } else {
                resultArea.append("No se pudo renombrar el archivo: " + path + "\n");
            }
        }
    }

    private static void processLinks(String htmlContent, String baseUrl, int currentDepth) {
        Matcher hrefMatcher = LINK_PATTERN.matcher(htmlContent);
        while (hrefMatcher.find()) {
            String link = hrefMatcher.group(1);
            processFoundLink(link, baseUrl, currentDepth);
        }

        Matcher srcMatcher = SRC_PATTERN.matcher(htmlContent);
        while (srcMatcher.find()) {
            String link = srcMatcher.group(1);
            processFoundLink(link, baseUrl, currentDepth);
        }
    }

    private static void processFoundLink(String link, String baseUrl, int currentDepth) {
        if (link.startsWith("javascript:") || link.startsWith("mailto:") || link.startsWith("#")) {
            return;
        }

        try {
            URL absoluteUrl = new URL(new URL(baseUrl), link);
            String normalizedUrl = absoluteUrl.toString().split("#")[0];

            if (!downloadedUrls.contains(normalizedUrl)) {
                urlQueue.add(normalizedUrl + "|" + (currentDepth + 1));
            }
        } catch (MalformedURLException e) {
            resultArea.append("Enlace inválido: " + link + " en " + baseUrl + "\n");
        }
    }

    private static String getLocalFilePath(String url) throws MalformedURLException {
        URL urlObj = new URL(url);
        String path = urlObj.getPath();

        path = path.split("\\?")[0].split("#")[0];

        if (path.endsWith("/")) {
            path += "index.html";
        } else {
            String[] segments = path.split("/");
            String lastSegment = segments[segments.length - 1];
            if (!lastSegment.contains(".")) {
                path += ".html";
            }
        }

        String host = urlObj.getHost();
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }

        Path localPath = Paths.get(outputDir, host, path).normalize();

        return localPath.toString();
    }

    private static String rewriteLinks(String htmlContent, String baseUrl, String filePath) throws MalformedURLException {
        String localBasePath = siteFolderName;

        Matcher hrefMatcher = LINK_PATTERN.matcher(htmlContent);
        StringBuffer sb = new StringBuffer();

        while (hrefMatcher.find()) {
            String originalLink = hrefMatcher.group(1);
            String newLink = convertToLocalLink(originalLink, baseUrl, localBasePath);
            hrefMatcher.appendReplacement(sb, "href=\"" + Matcher.quoteReplacement(newLink) + "\"");
        }
        hrefMatcher.appendTail(sb);

        Matcher srcMatcher = SRC_PATTERN.matcher(sb.toString());
        sb = new StringBuffer();

        while (srcMatcher.find()) {
            String originalLink = srcMatcher.group(1);
            String newLink = convertToLocalLink(originalLink, baseUrl, localBasePath);
            srcMatcher.appendReplacement(sb, "src=\"" + Matcher.quoteReplacement(newLink) + "\"");
        }
        srcMatcher.appendTail(sb);

        return sb.toString();
    }

    private static String convertToLocalLink(String originalLink, String baseUrl, String localBasePath) throws MalformedURLException {
        if (originalLink.startsWith("javascript:") || originalLink.startsWith("mailto:") || originalLink.startsWith("#")) {
            return originalLink;
        }

        URL resolvedUrl;
        if (originalLink.startsWith("http://") || originalLink.startsWith("https://")) {
            resolvedUrl = new URL(originalLink);
            if (resolvedUrl.getHost().equals(new URL(baseUrl).getHost())) {
                return handleLocalPath(resolvedUrl.getPath(), localBasePath);
            }
            return originalLink;
        }

        resolvedUrl = new URL(new URL(baseUrl), originalLink);
        return handleLocalPath(resolvedUrl.getPath(), localBasePath);
    }

    private static String handleLocalPath(String path, String localBasePath) {
        path = path.split("\\?")[0].split("#")[0];

        if (path.endsWith("/")) {
            path += "index.html";
        } else if (!path.contains(".")) {
            path += "/index.html";
        }

        return siteFolderName + path;
    }
}
