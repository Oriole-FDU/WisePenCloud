package com.oriole.wisepen.user.strategy.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationEmailSchool {

    private String domain;
    private String nameZh;
    private String nameOriginal;
    private List<String> originalNames;
    private String sourcePath;
    private Boolean translated;
    private String translationSource;
}