package com.example.siteplatform.document.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProjectDocumentDetailVO {
    private ProjectDocumentVO document;
    private List<ProjectDocumentVersionVO> versions;
    private List<ProjectDocumentActivityVO> activities;
}
