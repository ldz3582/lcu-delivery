package com.lcu.delivery.modules.upload;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传（商家执照/门店照、菜品图、售后凭证都用它）。
 *
 * 当前落本地磁盘 app.upload-dir（默认 ./uploads），通过 /uploads/** 静态访问；
 * 生产换 MinIO/OSS 只要把 transferTo 那段换成 SDK 上传即可，接口和返回结构不变。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /** 为空时按请求 Host 拼绝对地址（小程序 <image> 需要绝对 URL） */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM, "图片不能超过 5MB");
        }
        String ext = ext(file.getOriginalFilename(), file.getContentType());
        if (ext == null) {
            throw new BizException(ErrorCode.PARAM, "只支持 jpg / png / webp / gif 图片");
        }
        String sub = LocalDate.now().format(DIR_FMT);
        Path dir = Paths.get(uploadDir, sub).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());

        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                : publicBaseUrl.replaceAll("/$", "");
        String path = "/uploads/" + sub + "/" + name;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", base + path);
        data.put("path", path);
        data.put("size", file.getSize());
        return Result.ok(data);
    }

    private String ext(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "jpg";
        }
        if (name.endsWith(".png")) {
            return "png";
        }
        if (name.endsWith(".webp")) {
            return "webp";
        }
        if (name.endsWith(".gif")) {
            return "gif";
        }
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> null;
        };
    }
}
