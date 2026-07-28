package com.example.siteplatform.system.dto;

import com.example.siteplatform.system.entity.SystemMenu;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {
    private Long id;
    private Long parentId;
    private String clientType;
    private String menuCode;
    private String menuName;
    private String resourceType;
    private String routePath;
    private String permissionCode;
    private Integer sortOrder;
    private List<MenuVO> children = new ArrayList<>();

    public static MenuVO from(SystemMenu menu) {
        MenuVO vo = new MenuVO();
        BeanUtils.copyProperties(menu, vo);
        return vo;
    }

    public String getCode() { return menuCode; }
    public String getName() { return menuName; }
    public String getPath() { return routePath; }
    public String getType() { return resourceType; }
}
