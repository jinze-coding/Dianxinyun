package com.example.siteplatform.electricbox.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectricBoxServiceKeywordTest {

    @Mock
    private ElectricBoxMapper electricBoxMapper;
    @Mock
    private ProjectPermissionService permissionService;

    private ElectricBoxService service;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ElectricBoxMapper.class.getName()),
                ElectricBox.class);
        service = new ElectricBoxService();
        ReflectionTestUtils.setField(service, "electricBoxMapper", electricBoxMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        currentUser = new SysUser();
        currentUser.setId(7L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keywordMatchesAllSupportedFieldsWithoutSkippingProjectPermission() {
        when(electricBoxMapper.selectList(any())).thenReturn(List.of());

        service.list(3L, "ACTIVE", "  SSD-01  ", currentUser);

        ArgumentCaptor<Wrapper<ElectricBox>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(electricBoxMapper).selectList(captor.capture());
        LambdaQueryWrapper<ElectricBox> wrapper = (LambdaQueryWrapper<ElectricBox>) captor.getValue();
        String sql = wrapper.getSqlSegment();

        assertThat(sql)
                .contains("project_id", "status", "box_code", "box_name", "install_location",
                        "responsible_electrician_name", "responsible_electrician_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("%SSD-01%");
        verify(permissionService).checkProjectPermission(7L, 3L);
        verify(permissionService).requireSystemPermission(7L, 3L, SystemPermissionCodes.INSPECTION_VIEW);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keywordWithoutProjectIdRemainsLimitedToAuthorizedProjects() {
        ProjectInfo first = new ProjectInfo();
        first.setId(3L);
        ProjectInfo second = new ProjectInfo();
        second.setId(5L);
        when(permissionService.getUserProjects(7L)).thenReturn(List.of(first, second));
        when(electricBoxMapper.selectList(any())).thenReturn(List.of());

        service.list(null, "ACTIVE", "电工", currentUser);

        ArgumentCaptor<Wrapper<ElectricBox>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(electricBoxMapper).selectList(captor.capture());
        LambdaQueryWrapper<ElectricBox> wrapper = (LambdaQueryWrapper<ElectricBox>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("project_id", "responsible_electrician_name");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(3L, 5L, "%电工%");
    }
}
