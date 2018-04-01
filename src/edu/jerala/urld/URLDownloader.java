package edu.jerala.urld;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.net.URL;
import java.net.URLConnection;
import java.io.*;
import java.util.regex.*;

public class URLDownloader {

    public URLDownloader() {
    }

    /**
     * Parsing command line arguments and
     * determines the type of file to download.
     * @param args
     */
    public void download(String[] args) {
        String address = "";
        String pathToSave = "";
        boolean openFile = false;
        switch(args.length) {
            case 0: return;
            case 1:
                address = args[0];
                break;
            case 2:
                address = args[0];
                if(args[1].equals("false"))
                    break;
                else if(args[1].equals("true")) openFile = true;
                else pathToSave = args[1];
                break;
            case 3:
                address = args[0];
                pathToSave = args[1];
                openFile = Boolean.parseBoolean(args[2]);
                break;
        }
        try {
            URL url = new URL(address);
            String dest = destName(url, pathToSave);
            if(dest.endsWith(".html") || dest.endsWith(".htm")) downloadHTML(url, dest);
            else downloadFile(url, dest);
            if(openFile) openFile(dest);
        }
        catch(IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Determines encoding of the HTML page and downloads it.
     * Then calls methods to download images and links from the page source and
     * replaces the paths to them to local.
     * @param url
     * @param dest - path to save the file
     * @throws IOException
     */
    private void downloadHTML(URL url, String dest) throws IOException {
        String enc = getEncoding(url);
        if(enc == null) enc = "UTF8";
        InputStreamReader isr = new InputStreamReader(url.openStream(), enc);
        BufferedReader br = new BufferedReader(isr);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest) , isr.getEncoding()));
        String line;
        while ((line = br.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }
        br.close();
        writer.close();
        isr.close();

        downloadAllResources(url, dest);
        replaceSrcsInHTMLFile(dest, dest.substring(0, dest.lastIndexOf(".")) + "_files/", enc);
    }

    /**
     * Here is used jTidy library. (http://jtidy.sourceforge.net/)
     * Parse HTML page to find URLs of imgs and links and download them.
     * @param url - URL of the HTML page.
     * @param dest - path to the downloaded HTML page that will be replaced by the path to folder to save imgs and links.
     */
    private void downloadAllResources(URL url, String dest) {
        try {
            File dir = new File(dest.substring(0, dest.lastIndexOf(".")) + "_files/");
            dir.mkdir();
            InputStream input = url.openStream();
            Tidy tidy = new Tidy();
            tidy.setShowErrors(0);
            tidy.setQuiet(true);
            tidy.setShowWarnings(false);
            Document document = tidy.parseDOM(input, null);
            input.close();
            NodeList srcs = document.getElementsByTagName("link");

            String src;
            // download links
            for (int i = 0; i < srcs.getLength(); i++) {
                src = utils.correctSrc(url, srcs.item(i).getAttributes().getNamedItem("href").getNodeValue());
                downloadFile(new URL(src), dir.getAbsolutePath() + "/" + src.substring(src.lastIndexOf("/")+1));
            }

            // download images
            srcs = document.getElementsByTagName("img");
            for(int i = 0; i < srcs.getLength(); i++) {
                src = utils.correctSrc(url, srcs.item(i).getAttributes().getNamedItem("src").getNodeValue());
                downloadFile(new URL(src), dir.getAbsolutePath() + "/" + src.substring(src.lastIndexOf("/")+1));
            }
        } catch(IOException e) {
            e.getMessage();
        }
    }

    /**
     * Replaces the paths to imgs and links on the HTML page to local.
     * @param pathToHTML - path to downloaded HTML page.
     * @param pathToSrcs - path to folder with downloaded imgs and links.
     * @param encoding - encoding of HTML page.
     * @throws IOException
     */
    private void replaceSrcsInHTMLFile(String pathToHTML, String pathToSrcs, String encoding) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(pathToHTML)), encoding);
        final String regex = "<link.*?href=[\"'](.*?)[\"'].*?>|<img.*?src=[\"'](.+?)[\"'].*?>";

        pathToSrcs = new File(pathToSrcs).getAbsolutePath() + "/";

        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(content);
        List<String> imagesAndLinks = new ArrayList<String>(); // paths that will be replaced by locals

        while (matcher.find()) {// group 1 - links, group 2 - images.
            if(matcher.group(1) != null && (!imagesAndLinks.contains(matcher.group(1))))
                imagesAndLinks.add(matcher.group(1));
            else if(matcher.group(2) != null && (!imagesAndLinks.contains(matcher.group(2))))
                imagesAndLinks.add(matcher.group(2));
        }
        for(int i = 0; i < imagesAndLinks.size(); i++) {
            // replacement of paths(folder + name of file(after last "/") and remove unacceptable symbols from the name)
            content = content.replace(imagesAndLinks.get(i),
                    pathToSrcs + imagesAndLinks.get(i)
                            .substring(imagesAndLinks.get(i).lastIndexOf("/")).replaceAll("[?:\"/<>*]", ""));
        }
        Files.write(Paths.get(pathToHTML), content.getBytes(encoding));
    }

    /**
     * Downloads any file except HTML.
     * @param url
     * @param dest - path to save the file
     */
    private void downloadFile(URL url, String dest) {
        try {
            URLConnection conn = url.openConnection();
            InputStream in = conn.getInputStream();
            // removes unacceptable symbols from the name of file
            dest = dest.replace(dest.substring(dest.lastIndexOf("/") + 1), dest.substring(dest.lastIndexOf("/") + 1)
                    .replaceAll("[?:\"/<>*]", ""));
            FileOutputStream out = new FileOutputStream(dest);
            byte[] b = new byte[1024];
            int count;
            while ((count = in.read(b)) >= 0) {
                out.write(b, 0, count);
            }
            out.flush();
            out.close();
            in.close();
        } catch(IOException e) { // getMessage prints empty reference only, but not exception message
            System.out.println(e.toString());
        }
    }

    /**
     * @param url - path to the HTML page.
     * @return encoding of the HTML page.
     * @throws IOException
     */
    private String getEncoding(URL url) throws IOException {
        URLConnection uc = url.openConnection();
        // get charset from Content-Type
        String enc = uc.getHeaderField("Content-Type");
        Pattern p = Pattern.compile("=(.*?)$");
        Matcher m = p.matcher(enc);
        if(m.find())
            return m.group(1);
        return null;
    }

    /**
     * Corrects the path to where the file will be saved.
     * @param url - URL of file that will be downloaded.
     * @param path - absolute path to save the file.
     * @return the path to where the file will be saved.
     */
    private String destName(URL url, String path) {
        String dest = url.getPath();
        dest = utils.correctName(dest);
        if(path == "") return dest;

        File f = new File(path);
        if(!f.exists()) // then name of file is full name transferred as path to save
            dest = path.replaceAll("[\\\\/:]", "") + dest.substring(dest.lastIndexOf("."));
        else if(f.exists() && f.isDirectory())
            dest = f.getAbsolutePath() + "/" + dest;
        else if(f.exists() && f.isFile()) {
            System.out.print("File already exists. Do you want rename downloaded file? (Y or N): ");
            Scanner in = new Scanner(System.in);
            if(in.next().toLowerCase().equals("y")) {
                System.out.print("Enter new name of file: ");
                dest = in.next();
                dest = f.getAbsolutePath().replace(f.getAbsolutePath()
                        .substring(f.getAbsolutePath().lastIndexOf("\\")+1), utils.correctName(dest));
            }
            else dest = f.getAbsolutePath();
        }

        return dest;
    }

    private void openFile(String dest) throws IOException {
            Desktop desktop = Desktop.getDesktop();
            File myFile = new File(dest);
            desktop.open(myFile);
    }
}
