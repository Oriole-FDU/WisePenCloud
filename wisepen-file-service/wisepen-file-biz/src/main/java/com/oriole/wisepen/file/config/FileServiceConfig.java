package com.oriole.wisepen.file.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;


/**
 * @author Ian.xiong
 */
@Data
@Configuration
public class FileServiceConfig {

    /**
     * 服务实例ID，从配置文件读取。
     */
    @Value("${wisepen.file.instance-id:file-service-default}")
    private String instanceId;

}
