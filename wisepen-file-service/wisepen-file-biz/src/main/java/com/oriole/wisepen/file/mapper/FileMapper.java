package com.oriole.wisepen.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Ian.xiong
 */
@Mapper
public interface FileMapper extends BaseMapper<FileInfo> {
}
