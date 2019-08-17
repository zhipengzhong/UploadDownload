package com.young.demo.modules;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class FileController {


    private static final File UPLOAD_DIR;
    private static final HashMap<String, String> CONTENT_TYPE;

    static {
        UPLOAD_DIR = new File(System.getProperty("user.dir"), "/uploadFile/");
        if (!UPLOAD_DIR.exists()) {
            UPLOAD_DIR.mkdirs();
        }

        // content-type 对照表 http://tool.oschina.net/commons/
        CONTENT_TYPE = new HashMap<>();
        CONTENT_TYPE.put(".png", "image/png");
        CONTENT_TYPE.put(".jpg", "image/jpeg");
        CONTENT_TYPE.put(".mp4", "video/mpeg4");
    }

    @PostMapping("upload")
    public String upload(MultipartFile uploadFile, HttpServletRequest request) {
//        String path = request.getSession().getServletContext().getRealPath("/uploadFile/");
        try {
            String filename = uploadFile.getOriginalFilename();
            uploadFile.transferTo(new File(UPLOAD_DIR, filename));
            return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/download/" + filename;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "上传失败";
    }

    @RequestMapping("download/{fileId}")
    @ResponseBody
    public void download(@PathVariable String fileId, HttpServletRequest request, HttpServletResponse response) {
        if (fileId == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File file = new File(UPLOAD_DIR, fileId);
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String range = request.getHeader("range");
        String referer = request.getHeader("referer");
        if (range != null && referer != null) {
            String[] split = range.split("bytes=|-");
            long begin = 0;
            if (split.length >= 2) {
                begin = Long.valueOf(split[1]);
            }
            long end = file.length() - 1;
            if (split.length >= 3) {
                end = Long.valueOf(split[2]);
            }
            long len = (end - begin) + 1;

            if (end > file.length()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            FileInputStream inputStream = null;
            ServletOutputStream os = null;
            try {
                inputStream = new FileInputStream(file);
                inputStream.skip(begin);

                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setContentType(getContentType(fileId));
                response.addHeader("Content-Range", "bytes " + begin + "-" + end + "/" + file.length());
                response.addHeader("Content-Length", String.valueOf(len));
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("Cache-control", "private");
                response.addHeader("Content-Disposition", "filename=" + file.getName());
                response.addHeader("Last-Modified", new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss Z", Locale.ENGLISH).format(file.lastModified()) + " GMT");

                os = response.getOutputStream();

                byte[] buf = new byte[1024];
                while (len > 0) {
                    inputStream.read(buf);
                    long l = len > 1024 ? 1024 : len;
                    os.write(buf, 0, (int) l);
                    os.flush();
                    len -= l;
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            FileInputStream inputStream = null;
            ServletOutputStream os = null;
            try {
                inputStream = new FileInputStream(file);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(getContentType(fileId));
                response.addHeader("Content-Length", String.valueOf(file.length()));
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("Cache-control", "private");
                response.addHeader("Content-Disposition", "filename=" + file.getName());
                response.addHeader("Last-Modified", new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss Z", Locale.ENGLISH).format(file.lastModified()) + " GMT");

                os = response.getOutputStream();

                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                    os.flush();
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public String getContentType(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        if (CONTENT_TYPE.containsKey(suffix)) {
            return CONTENT_TYPE.get(suffix);
        } else {
            return "application/octet-stream";
        }
    }
}
