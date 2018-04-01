package edu.jerala.urld;

import java.net.URL;

public class utils {
    public static String correctName(String name) {
        if(name.equals("/") || name.equals("")) name = "index";
        if(name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1, name.length());
        if(!name.contains(".")) name += ".html";
        if(name.contains(".php")) name = name.replace(".php", ".html");
        return name;
    }

    public static String correctSrc(URL url, String src) {
        if(src.startsWith(".")) src = src.substring(1);
        if(!src.startsWith("http"))
            src = url.getProtocol() + "://" + url.getHost() + "/" + src;
        return src;
    }
}
