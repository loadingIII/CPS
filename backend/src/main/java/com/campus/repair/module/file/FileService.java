package com.campus.repair.module.file;

import com.campus.repair.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储。R2 迁移对象存储时仅需替换本实现（接口不变）。
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    private final String uploadDir;

    public FileService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /** 保存上传图片，返回可访问 URL（/uploads/yyyyMM/uuid.ext） */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.param("上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw BusinessException.param("仅支持 jpg/jpeg/png/webp 格式图片");
        }
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(month);
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            file.transferTo(dir.resolve(name).toFile());
            return "/uploads/" + month + "/" + name;
        } catch (Exception e) {
            throw new BusinessException(com.campus.repair.common.ErrorCode.SYSTEM_ERROR,
                    "文件保存失败: " + e.getMessage());
        }
    }
}
